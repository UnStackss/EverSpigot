package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;

public class VanillaPackResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final BuiltInMetadata metadata;
    private final Set<String> namespaces;
    private final List<Path> rootPaths;
    private final Map<PackType, List<Path>> pathsForType;

    VanillaPackResources(
        PackLocationInfo info, BuiltInMetadata metadata, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> namespacePaths
    ) {
        this.location = info;
        this.metadata = metadata;
        this.namespaces = namespaces;
        this.rootPaths = rootPaths;
        this.pathsForType = namespacePaths;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        FileUtil.validatePath(segments);
        List<String> list = List.of(segments);

        for (Path path : this.rootPaths) {
            Path path2 = FileUtil.resolvePath(path, list);
            if (Files.exists(path2) && PathPackResources.validatePath(path2)) {
                return IoSupplier.create(path2);
            }
        }

        return null;
    }

    public void listRawPaths(PackType type, ResourceLocation path, Consumer<Path> consumer) {
        FileUtil.decomposePath(path.getPath()).ifSuccess(segments -> {
            String string = path.getNamespace();

            for (Path pathx : this.pathsForType.get(type)) {
                Path path2 = pathx.resolve(string);
                consumer.accept(FileUtil.resolvePath(path2, (List<String>)segments));
            }
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", path, error.message()));
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput consumer) {
        FileUtil.decomposePath(prefix).ifSuccess(segments -> {
            List<Path> list = this.pathsForType.get(type);
            int i = list.size();
            if (i == 1) {
                getResources(consumer, namespace, list.get(0), (List<String>)segments);
            } else if (i > 1) {
                Map<ResourceLocation, IoSupplier<InputStream>> map = new HashMap<>();

                for (int j = 0; j < i - 1; j++) {
                    getResources(map::putIfAbsent, namespace, list.get(j), (List<String>)segments);
                }

                Path path = list.get(i - 1);
                if (map.isEmpty()) {
                    getResources(consumer, namespace, path, (List<String>)segments);
                } else {
                    getResources(map::putIfAbsent, namespace, path, (List<String>)segments);
                    map.forEach(consumer);
                }
            }
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", prefix, error.message()));
    }

    private static void getResources(PackResources.ResourceOutput consumer, String namespace, Path root, List<String> prefixSegments) {
        Path path = root.resolve(namespace);
        PathPackResources.listPath(namespace, path, prefixSegments, consumer);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        return FileUtil.decomposePath(id.getPath()).mapOrElse(segments -> {
            String string = id.getNamespace();

            for (Path path : this.pathsForType.get(type)) {
                Path path2 = FileUtil.resolvePath(path.resolve(string), (List<String>)segments);
                if (Files.exists(path2) && PathPackResources.validatePath(path2)) {
                    return IoSupplier.create(path2);
                }
            }

            return null;
        }, error -> {
            LOGGER.error("Invalid path {}: {}", id, error.message());
            return null;
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return this.namespaces;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metaReader) {
        IoSupplier<InputStream> ioSupplier = this.getRootResource("pack.mcmeta");
        if (ioSupplier != null) {
            try (InputStream inputStream = ioSupplier.get()) {
                T object = AbstractPackResources.getMetadataFromStream(metaReader, inputStream);
                if (object != null) {
                    return object;
                }

                return this.metadata.get(metaReader);
            } catch (IOException var8) {
            }
        }

        return this.metadata.get(metaReader);
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }

    public ResourceProvider asProvider() {
        return id -> Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, id)).map(stream -> new Resource(this, (IoSupplier<InputStream>)stream));
    }
}
