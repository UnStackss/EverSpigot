package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class StructureTemplateManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String STRUCTURE_RESOURCE_DIRECTORY_NAME = "structure";
    private static final String STRUCTURE_GENERATED_DIRECTORY_NAME = "structures";
    private static final String STRUCTURE_FILE_EXTENSION = ".nbt";
    private static final String STRUCTURE_TEXT_FILE_EXTENSION = ".snbt";
    public final Map<ResourceLocation, Optional<StructureTemplate>> structureRepository = Maps.newConcurrentMap();
    private final DataFixer fixerUpper;
    private ResourceManager resourceManager;
    private final Path generatedDir;
    private final List<StructureTemplateManager.Source> sources;
    private final HolderGetter<Block> blockLookup;
    private static final FileToIdConverter RESOURCE_LISTER = new FileToIdConverter("structure", ".nbt");

    public StructureTemplateManager(
        ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, HolderGetter<Block> blockLookup
    ) {
        this.resourceManager = resourceManager;
        this.fixerUpper = dataFixer;
        this.generatedDir = session.getLevelPath(LevelResource.GENERATED_DIR).normalize();
        this.blockLookup = blockLookup;
        Builder<StructureTemplateManager.Source> builder = ImmutableList.builder();
        builder.add(new StructureTemplateManager.Source(this::loadFromGenerated, this::listGenerated));
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            builder.add(new StructureTemplateManager.Source(this::loadFromTestStructures, this::listTestStructures));
        }

        builder.add(new StructureTemplateManager.Source(this::loadFromResource, this::listResources));
        this.sources = builder.build();
    }

    public StructureTemplate getOrCreate(ResourceLocation id) {
        Optional<StructureTemplate> optional = this.get(id);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            StructureTemplate structureTemplate = new StructureTemplate();
            this.structureRepository.put(id, Optional.of(structureTemplate));
            return structureTemplate;
        }
    }

    public Optional<StructureTemplate> get(ResourceLocation id) {
        return this.structureRepository.computeIfAbsent(id, this::tryLoad);
    }

    public Stream<ResourceLocation> listTemplates() {
        return this.sources.stream().flatMap(provider -> provider.lister().get()).distinct();
    }

    private Optional<StructureTemplate> tryLoad(ResourceLocation id) {
        for (StructureTemplateManager.Source source : this.sources) {
            try {
                Optional<StructureTemplate> optional = source.loader().apply(id);
                if (optional.isPresent()) {
                    return optional;
                }
            } catch (Exception var5) {
            }
        }

        return Optional.empty();
    }

    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        this.structureRepository.clear();
    }

    public Optional<StructureTemplate> loadFromResource(ResourceLocation id) {
        ResourceLocation resourceLocation = RESOURCE_LISTER.idToFile(id);
        return this.load(() -> this.resourceManager.open(resourceLocation), throwable -> LOGGER.error("Couldn't load structure {}", id, throwable));
    }

    private Stream<ResourceLocation> listResources() {
        return RESOURCE_LISTER.listMatchingResources(this.resourceManager).keySet().stream().map(RESOURCE_LISTER::fileToId);
    }

    private Optional<StructureTemplate> loadFromTestStructures(ResourceLocation id) {
        return this.loadFromSnbt(id, Paths.get(StructureUtils.testStructuresDir));
    }

    private Stream<ResourceLocation> listTestStructures() {
        Path path = Paths.get(StructureUtils.testStructuresDir);
        if (!Files.isDirectory(path)) {
            return Stream.empty();
        } else {
            List<ResourceLocation> list = new ArrayList<>();
            this.listFolderContents(path, "minecraft", ".snbt", list::add);
            return list.stream();
        }
    }

    public Optional<StructureTemplate> loadFromGenerated(ResourceLocation id) {
        if (!Files.isDirectory(this.generatedDir)) {
            return Optional.empty();
        } else {
            Path path = this.createAndValidatePathToGeneratedStructure(id, ".nbt");
            return this.load(() -> new FileInputStream(path.toFile()), throwable -> LOGGER.error("Couldn't load structure from {}", path, throwable));
        }
    }

    private Stream<ResourceLocation> listGenerated() {
        if (!Files.isDirectory(this.generatedDir)) {
            return Stream.empty();
        } else {
            try {
                List<ResourceLocation> list = new ArrayList<>();

                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(this.generatedDir, pathx -> Files.isDirectory(pathx))) {
                    for (Path path : directoryStream) {
                        String string = path.getFileName().toString();
                        Path path2 = path.resolve("structures");
                        this.listFolderContents(path2, string, ".nbt", list::add);
                    }
                }

                return list.stream();
            } catch (IOException var9) {
                return Stream.empty();
            }
        }
    }

    private void listFolderContents(Path directory, String namespace, String fileExtension, Consumer<ResourceLocation> idConsumer) {
        int i = fileExtension.length();
        Function<String, String> function = filename -> filename.substring(0, filename.length() - i);

        try (Stream<Path> stream = Files.find(
                directory, Integer.MAX_VALUE, (path, attributes) -> attributes.isRegularFile() && path.toString().endsWith(fileExtension)
            )) {
            stream.forEach(path -> {
                try {
                    idConsumer.accept(ResourceLocation.fromNamespaceAndPath(namespace, function.apply(this.relativize(directory, path))));
                } catch (ResourceLocationException var7x) {
                    LOGGER.error("Invalid location while listing folder {} contents", directory, var7x);
                }
            });
        } catch (IOException var12) {
            LOGGER.error("Failed to list folder {} contents", directory, var12);
        }
    }

    private String relativize(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separator, "/");
    }

    private Optional<StructureTemplate> loadFromSnbt(ResourceLocation id, Path path) {
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        } else {
            Path path2 = FileUtil.createPathToResource(path, id.getPath(), ".snbt");

            try {
                Optional var6;
                try (BufferedReader bufferedReader = Files.newBufferedReader(path2)) {
                    String string = IOUtils.toString(bufferedReader);
                    var6 = Optional.of(this.readStructure(NbtUtils.snbtToStructure(string)));
                }

                return var6;
            } catch (NoSuchFileException var9) {
                return Optional.empty();
            } catch (CommandSyntaxException | IOException var10) {
                LOGGER.error("Couldn't load structure from {}", path2, var10);
                return Optional.empty();
            }
        }
    }

    private Optional<StructureTemplate> load(StructureTemplateManager.InputStreamOpener opener, Consumer<Throwable> exceptionConsumer) {
        try {
            Optional var5;
            try (
                InputStream inputStream = opener.open();
                InputStream inputStream2 = new FastBufferedInputStream(inputStream);
            ) {
                var5 = Optional.of(this.readStructure(inputStream2));
            }

            return var5;
        } catch (FileNotFoundException var11) {
            return Optional.empty();
        } catch (Throwable var12) {
            exceptionConsumer.accept(var12);
            return Optional.empty();
        }
    }

    public StructureTemplate readStructure(InputStream templateIInputStream) throws IOException {
        CompoundTag compoundTag = NbtIo.readCompressed(templateIInputStream, NbtAccounter.unlimitedHeap());
        return this.readStructure(compoundTag);
    }

    public StructureTemplate readStructure(CompoundTag nbt) {
        StructureTemplate structureTemplate = new StructureTemplate();
        int i = NbtUtils.getDataVersion(nbt, 500);
        structureTemplate.load(this.blockLookup, ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.STRUCTURE, nbt, i, SharedConstants.getCurrentVersion().getDataVersion().getVersion())); // Paper
        return structureTemplate;
    }

    public boolean save(ResourceLocation id) {
        Optional<StructureTemplate> optional = this.structureRepository.get(id);
        if (optional.isEmpty()) {
            return false;
        } else {
            StructureTemplate structureTemplate = optional.get();
            Path path = this.createAndValidatePathToGeneratedStructure(id, ".nbt");
            Path path2 = path.getParent();
            if (path2 == null) {
                return false;
            } else {
                try {
                    Files.createDirectories(Files.exists(path2) ? path2.toRealPath() : path2);
                } catch (IOException var13) {
                    LOGGER.error("Failed to create parent directory: {}", path2);
                    return false;
                }

                CompoundTag compoundTag = structureTemplate.save(new CompoundTag());

                try {
                    try (OutputStream outputStream = new FileOutputStream(path.toFile())) {
                        NbtIo.writeCompressed(compoundTag, outputStream);
                    }

                    return true;
                } catch (Throwable var12) {
                    return false;
                }
            }
        }
    }

    public Path createAndValidatePathToGeneratedStructure(ResourceLocation id, String extension) {
        if (id.getPath().contains("//")) {
            throw new ResourceLocationException("Invalid resource path: " + id);
        } else {
            try {
                Path path = this.generatedDir.resolve(id.getNamespace());
                Path path2 = path.resolve("structures");
                Path path3 = FileUtil.createPathToResource(path2, id.getPath(), extension);
                if (path3.startsWith(this.generatedDir) && FileUtil.isPathNormalized(path3) && FileUtil.isPathPortable(path3)) {
                    return path3;
                } else {
                    throw new ResourceLocationException("Invalid resource path: " + path3);
                }
            } catch (InvalidPathException var6) {
                throw new ResourceLocationException("Invalid resource path: " + id, var6);
            }
        }
    }

    public void remove(ResourceLocation id) {
        this.structureRepository.remove(id);
    }

    @FunctionalInterface
    interface InputStreamOpener {
        InputStream open() throws IOException;
    }

    static record Source(Function<ResourceLocation, Optional<StructureTemplate>> loader, Supplier<Stream<ResourceLocation>> lister) {
    }
}
