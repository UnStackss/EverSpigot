package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.nbt.visitors.SkipFields;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import net.minecraft.world.level.validation.PathAllowList;
import org.slf4j.Logger;

public class LevelStorageSource {

    static final Logger LOGGER = LogUtils.getLogger();
    static final DateTimeFormatter FORMATTER = FileNameDateFormatter.create();
    private static final String TAG_DATA = "Data";
    private static final PathMatcher NO_SYMLINKS_ALLOWED = (path) -> {
        return false;
    };
    public static final String ALLOWED_SYMLINKS_CONFIG_NAME = "allowed_symlinks.txt";
    private static final int UNCOMPRESSED_NBT_QUOTA = 104857600;
    private static final int DISK_SPACE_WARNING_THRESHOLD = 67108864;
    public final Path baseDir;
    private final Path backupDir;
    final DataFixer fixerUpper;
    private final DirectoryValidator worldDirValidator;

    public LevelStorageSource(Path savesDirectory, Path backupsDirectory, DirectoryValidator symlinkFinder, DataFixer dataFixer) {
        this.fixerUpper = dataFixer;

        try {
            FileUtil.createDirectoriesSafe(savesDirectory);
        } catch (IOException ioexception) {
            throw new UncheckedIOException(ioexception);
        }

        this.baseDir = savesDirectory;
        this.backupDir = backupsDirectory;
        this.worldDirValidator = symlinkFinder;
    }

    public static DirectoryValidator parseValidator(Path allowedSymlinksFile) {
        if (Files.exists(allowedSymlinksFile, new LinkOption[0])) {
            try {
                BufferedReader bufferedreader = Files.newBufferedReader(allowedSymlinksFile);

                DirectoryValidator directoryvalidator;

                try {
                    directoryvalidator = new DirectoryValidator(PathAllowList.readPlain(bufferedreader));
                } catch (Throwable throwable) {
                    if (bufferedreader != null) {
                        try {
                            bufferedreader.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }
                    }

                    throw throwable;
                }

                if (bufferedreader != null) {
                    bufferedreader.close();
                }

                return directoryvalidator;
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to parse {}, disallowing all symbolic links", "allowed_symlinks.txt", exception);
            }
        }

        return new DirectoryValidator(LevelStorageSource.NO_SYMLINKS_ALLOWED);
    }

    public static LevelStorageSource createDefault(Path path) {
        DirectoryValidator directoryvalidator = LevelStorageSource.parseValidator(path.resolve("allowed_symlinks.txt"));

        return new LevelStorageSource(path, path.resolve("../backups"), directoryvalidator, DataFixers.getDataFixer());
    }

    public static WorldDataConfiguration readDataConfig(Dynamic<?> dynamic) {
        DataResult<WorldDataConfiguration> dataresult = WorldDataConfiguration.CODEC.parse(dynamic); // CraftBukkit - decompile error
        Logger logger = LevelStorageSource.LOGGER;

        Objects.requireNonNull(logger);
        return (WorldDataConfiguration) dataresult.resultOrPartial(logger::error).orElse(WorldDataConfiguration.DEFAULT);
    }

    public static WorldLoader.PackConfig getPackConfig(Dynamic<?> dynamic, PackRepository dataPackManager, boolean safeMode) {
        return new WorldLoader.PackConfig(dataPackManager, LevelStorageSource.readDataConfig(dynamic), safeMode, false);
    }

    public static LevelDataAndDimensions getLevelDataAndDimensions(Dynamic<?> dynamic, WorldDataConfiguration dataConfiguration, Registry<LevelStem> dimensionsRegistry, RegistryAccess.Frozen registryManager) {
        Dynamic<?> dynamic1 = RegistryOps.injectRegistryContext(dynamic, registryManager);
        Dynamic<?> dynamic2 = dynamic1.get("WorldGenSettings").orElseEmptyMap();
        WorldGenSettings generatorsettings = (WorldGenSettings) WorldGenSettings.CODEC.parse(dynamic2).getOrThrow();
        LevelSettings worldsettings = LevelSettings.parse(dynamic1, dataConfiguration);
        WorldDimensions.Complete worlddimensions_b = generatorsettings.dimensions().bake(dimensionsRegistry);
        Lifecycle lifecycle = worlddimensions_b.lifecycle().add(registryManager.allRegistriesLifecycle());
        PrimaryLevelData worlddataserver = PrimaryLevelData.parse(dynamic1, worldsettings, worlddimensions_b.specialWorldProperty(), generatorsettings.options(), lifecycle);
        worlddataserver.pdc = ((Dynamic<Tag>) dynamic1).getElement("BukkitValues", null); // CraftBukkit - Add PDC to world

        return new LevelDataAndDimensions(worlddataserver, worlddimensions_b);
    }

    public String getName() {
        return "Anvil";
    }

    public LevelStorageSource.LevelCandidates findLevelCandidates() throws LevelStorageException {
        if (!Files.isDirectory(this.baseDir, new LinkOption[0])) {
            throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
        } else {
            try {
                Stream<Path> stream = Files.list(this.baseDir);

                LevelStorageSource.LevelCandidates convertable_a;

                try {
                    List<LevelStorageSource.LevelDirectory> list = stream.filter((path) -> {
                        return Files.isDirectory(path, new LinkOption[0]);
                    }).map(LevelStorageSource.LevelDirectory::new).filter((convertable_b) -> {
                        return Files.isRegularFile(convertable_b.dataFile(), new LinkOption[0]) || Files.isRegularFile(convertable_b.oldDataFile(), new LinkOption[0]);
                    }).toList();

                    convertable_a = new LevelStorageSource.LevelCandidates(list);
                } catch (Throwable throwable) {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }
                    }

                    throw throwable;
                }

                if (stream != null) {
                    stream.close();
                }

                return convertable_a;
            } catch (IOException ioexception) {
                throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
            }
        }
    }

    public CompletableFuture<List<LevelSummary>> loadLevelSummaries(LevelStorageSource.LevelCandidates levels) {
        List<CompletableFuture<LevelSummary>> list = new ArrayList(levels.levels.size());
        Iterator iterator = levels.levels.iterator();

        while (iterator.hasNext()) {
            LevelStorageSource.LevelDirectory convertable_b = (LevelStorageSource.LevelDirectory) iterator.next();

            list.add(CompletableFuture.supplyAsync(() -> {
                boolean flag;

                try {
                    flag = DirectoryLock.isLocked(convertable_b.path());
                } catch (Exception exception) {
                    LevelStorageSource.LOGGER.warn("Failed to read {} lock", convertable_b.path(), exception);
                    return null;
                }

                try {
                    return this.readLevelSummary(convertable_b, flag);
                } catch (OutOfMemoryError outofmemoryerror) {
                    MemoryReserve.release();
                    System.gc();
                    String s = "Ran out of memory trying to read summary of world folder \"" + convertable_b.directoryName() + "\"";

                    LevelStorageSource.LOGGER.error(LogUtils.FATAL_MARKER, s);
                    OutOfMemoryError outofmemoryerror1 = new OutOfMemoryError("Ran out of memory reading level data");

                    outofmemoryerror1.initCause(outofmemoryerror);
                    CrashReport crashreport = CrashReport.forThrowable(outofmemoryerror1, s);
                    CrashReportCategory crashreportsystemdetails = crashreport.addCategory("World details");

                    crashreportsystemdetails.setDetail("Folder Name", (Object) convertable_b.directoryName());

                    try {
                        long i = Files.size(convertable_b.dataFile());

                        crashreportsystemdetails.setDetail("level.dat size", (Object) i);
                    } catch (IOException ioexception) {
                        crashreportsystemdetails.setDetailError("level.dat size", ioexception);
                    }

                    throw new ReportedException(crashreport);
                }
            }, Util.backgroundExecutor()));
        }

        return Util.sequenceFailFastAndCancel(list).thenApply((list1) -> {
            return list1.stream().filter(Objects::nonNull).sorted().toList();
        });
    }

    private int getStorageVersion() {
        return 19133;
    }

    static CompoundTag readLevelDataTagRaw(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(104857600L));
    }

    static Dynamic<?> readLevelDataTagFixed(Path path, DataFixer dataFixer) throws IOException {
        CompoundTag nbttagcompound = LevelStorageSource.readLevelDataTagRaw(path);
        CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");
        int i = NbtUtils.getDataVersion(nbttagcompound1, -1); final int version = i; // Paper - obfuscation helpers
        Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(dataFixer, new Dynamic(NbtOps.INSTANCE, nbttagcompound1), i);

        // Paper start - replace data conversion system
        dynamic = dynamic.update("Player", (dynamic1) -> {
            return new Dynamic<>(
                NbtOps.INSTANCE,
                ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(
                    ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER,
                    (net.minecraft.nbt.CompoundTag)dynamic1.getValue(),
                    version, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()
                )
            );
        });
        // Paper end - replace data conversion system
        dynamic = dynamic.update("WorldGenSettings", (dynamic1) -> {
            return DataFixTypes.WORLD_GEN_SETTINGS.updateToCurrentVersion(dataFixer, dynamic1, i);
        });
        return dynamic;
    }

    private LevelSummary readLevelSummary(LevelStorageSource.LevelDirectory save, boolean locked) {
        Path path = save.dataFile();

        if (Files.exists(path, new LinkOption[0])) {
            try {
                if (Files.isSymbolicLink(path)) {
                    List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateSymlink(path);

                    if (!list.isEmpty()) {
                        LevelStorageSource.LOGGER.warn("{}", ContentValidationException.getMessage(path, list));
                        return new LevelSummary.SymlinkLevelSummary(save.directoryName(), save.iconFile());
                    }
                }

                Tag nbtbase = LevelStorageSource.readLightweightData(path);

                if (nbtbase instanceof CompoundTag) {
                    CompoundTag nbttagcompound = (CompoundTag) nbtbase;
                    CompoundTag nbttagcompound1 = nbttagcompound.getCompound("Data");
                    int i = NbtUtils.getDataVersion(nbttagcompound1, -1);
                    Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(this.fixerUpper, new Dynamic(NbtOps.INSTANCE, nbttagcompound1), i);

                    return this.makeLevelSummary(dynamic, save, locked);
                }

                LevelStorageSource.LOGGER.warn("Invalid root tag in {}", path);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Exception reading {}", path, exception);
            }
        }

        return new LevelSummary.CorruptedLevelSummary(save.directoryName(), save.iconFile(), LevelStorageSource.getFileModificationTime(save));
    }

    private static long getFileModificationTime(LevelStorageSource.LevelDirectory save) {
        Instant instant = LevelStorageSource.getFileModificationTime(save.dataFile());

        if (instant == null) {
            instant = LevelStorageSource.getFileModificationTime(save.oldDataFile());
        }

        return instant == null ? -1L : instant.toEpochMilli();
    }

    @Nullable
    static Instant getFileModificationTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ioexception) {
            return null;
        }
    }

    LevelSummary makeLevelSummary(Dynamic<?> dynamic, LevelStorageSource.LevelDirectory save, boolean locked) {
        LevelVersion levelversion = LevelVersion.parse(dynamic);
        int i = levelversion.levelDataVersion();

        if (i != 19132 && i != 19133) {
            throw new NbtFormatException("Unknown data version: " + Integer.toHexString(i));
        } else {
            boolean flag1 = i != this.getStorageVersion();
            Path path = save.iconFile();
            WorldDataConfiguration worlddataconfiguration = LevelStorageSource.readDataConfig(dynamic);
            LevelSettings worldsettings = LevelSettings.parse(dynamic, worlddataconfiguration);
            FeatureFlagSet featureflagset = LevelStorageSource.parseFeatureFlagsFromSummary(dynamic);
            boolean flag2 = FeatureFlags.isExperimental(featureflagset);

            return new LevelSummary(worldsettings, levelversion, save.directoryName(), flag1, locked, flag2, path);
        }
    }

    private static FeatureFlagSet parseFeatureFlagsFromSummary(Dynamic<?> levelData) {
        Set<ResourceLocation> set = (Set) levelData.get("enabled_features").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().map(ResourceLocation::tryParse).stream();
        }).collect(Collectors.toSet());

        return FeatureFlags.REGISTRY.fromNames(set, (minecraftkey) -> {
        });
    }

    @Nullable
    private static Tag readLightweightData(Path path) throws IOException {
        SkipFields skipfields = new SkipFields(new FieldSelector[]{new FieldSelector("Data", CompoundTag.TYPE, "Player"), new FieldSelector("Data", CompoundTag.TYPE, "WorldGenSettings")});

        NbtIo.parseCompressed(path, skipfields, NbtAccounter.create(104857600L));
        return skipfields.getResult();
    }

    public boolean isNewLevelIdAcceptable(String name) {
        try {
            Path path = this.getLevelPath(name);

            Files.createDirectory(path);
            Files.deleteIfExists(path);
            return true;
        } catch (IOException ioexception) {
            return false;
        }
    }

    public boolean levelExists(String name) {
        try {
            return Files.isDirectory(this.getLevelPath(name), new LinkOption[0]);
        } catch (InvalidPathException invalidpathexception) {
            return false;
        }
    }

    public Path getLevelPath(String name) {
        return this.baseDir.resolve(name);
    }

    public Path getBaseDir() {
        return this.baseDir;
    }

    public Path getBackupPath() {
        return this.backupDir;
    }

    public LevelStorageSource.LevelStorageAccess validateAndCreateAccess(String s, ResourceKey<LevelStem> dimensionType) throws IOException, ContentValidationException { // CraftBukkit
        Path path = this.getLevelPath(s);
        List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateDirectory(path, true);

        if (!list.isEmpty()) {
            throw new ContentValidationException(path, list);
        } else {
            return new LevelStorageSource.LevelStorageAccess(s, path, dimensionType); // CraftBukkit
        }
    }

    public LevelStorageSource.LevelStorageAccess createAccess(String s, ResourceKey<LevelStem> dimensionType) throws IOException { // CraftBukkit
        Path path = this.getLevelPath(s);

        return new LevelStorageSource.LevelStorageAccess(s, path, dimensionType); // CraftBukkit
    }

    public DirectoryValidator getWorldDirValidator() {
        return this.worldDirValidator;
    }

    // CraftBukkit start
    public static Path getStorageFolder(Path path, ResourceKey<LevelStem> dimensionType) {
        if (dimensionType == LevelStem.OVERWORLD) {
            return path;
        } else if (dimensionType == LevelStem.NETHER) {
            return path.resolve("DIM-1");
        } else if (dimensionType == LevelStem.END) {
            return path.resolve("DIM1");
        } else {
            return path.resolve("dimensions").resolve(dimensionType.location().getNamespace()).resolve(dimensionType.location().getPath());
        }
    }
    // CraftBukkit end

    public static record LevelCandidates(List<LevelStorageSource.LevelDirectory> levels) implements Iterable<LevelStorageSource.LevelDirectory> {

        public boolean isEmpty() {
            return this.levels.isEmpty();
        }

        public Iterator<LevelStorageSource.LevelDirectory> iterator() {
            return this.levels.iterator();
        }
    }

    public static record LevelDirectory(Path path) {

        public String directoryName() {
            return this.path.getFileName().toString();
        }

        public Path dataFile() {
            return this.resourcePath(LevelResource.LEVEL_DATA_FILE);
        }

        public Path oldDataFile() {
            return this.resourcePath(LevelResource.OLD_LEVEL_DATA_FILE);
        }

        public Path corruptedDataFile(LocalDateTime dateTime) {
            Path path = this.path;
            String s = LevelResource.LEVEL_DATA_FILE.getId();

            return path.resolve(s + "_corrupted_" + dateTime.format(LevelStorageSource.FORMATTER));
        }

        public Path rawDataFile(LocalDateTime dateTime) {
            Path path = this.path;
            String s = LevelResource.LEVEL_DATA_FILE.getId();

            return path.resolve(s + "_raw_" + dateTime.format(LevelStorageSource.FORMATTER));
        }

        public Path iconFile() {
            return this.resourcePath(LevelResource.ICON_FILE);
        }

        public Path lockFile() {
            return this.resourcePath(LevelResource.LOCK_FILE);
        }

        public Path resourcePath(LevelResource savePath) {
            return this.path.resolve(savePath.getId());
        }
    }

    public class LevelStorageAccess implements AutoCloseable {

        final DirectoryLock lock;
        public final LevelStorageSource.LevelDirectory levelDirectory;
        private final String levelId;
        private final Map<LevelResource, Path> resources = Maps.newHashMap();
        // CraftBukkit start
        public final ResourceKey<LevelStem> dimensionType;

        LevelStorageAccess(final String s, final Path path, final ResourceKey<LevelStem> dimensionType) throws IOException {
            this.dimensionType = dimensionType;
            // CraftBukkit end
            this.levelId = s;
            this.levelDirectory = new LevelStorageSource.LevelDirectory(path);
            this.lock = DirectoryLock.create(path);
        }

        public long estimateDiskSpace() {
            try {
                return Files.getFileStore(this.levelDirectory.path).getUsableSpace();
            } catch (Exception exception) {
                return Long.MAX_VALUE;
            }
        }

        public boolean checkForLowDiskSpace() {
            return this.estimateDiskSpace() < 67108864L;
        }

        public void safeClose() {
            try {
                this.close();
            } catch (IOException ioexception) {
                LevelStorageSource.LOGGER.warn("Failed to unlock access to level {}", this.getLevelId(), ioexception);
            }

        }

        public LevelStorageSource parent() {
            return LevelStorageSource.this;
        }

        public LevelStorageSource.LevelDirectory getLevelDirectory() {
            return this.levelDirectory;
        }

        public String getLevelId() {
            return this.levelId;
        }

        public Path getLevelPath(LevelResource savePath) {
            Map<LevelResource, Path> map = this.resources; // CraftBukkit - decompile error
            LevelStorageSource.LevelDirectory convertable_b = this.levelDirectory;

            Objects.requireNonNull(this.levelDirectory);
            return (Path) map.computeIfAbsent(savePath, convertable_b::resourcePath);
        }

        public Path getDimensionPath(ResourceKey<Level> key) {
            return LevelStorageSource.getStorageFolder(this.levelDirectory.path(), this.dimensionType); // CraftBukkit
        }

        private void checkLock() {
            if (!this.lock.isValid()) {
                throw new IllegalStateException("Lock is no longer valid");
            }
        }

        public PlayerDataStorage createPlayerStorage() {
            this.checkLock();
            return new PlayerDataStorage(this, LevelStorageSource.this.fixerUpper);
        }

        public LevelSummary getSummary(Dynamic<?> dynamic) {
            this.checkLock();
            return LevelStorageSource.this.makeLevelSummary(dynamic, this.levelDirectory, false);
        }

        public Dynamic<?> getDataTag() throws IOException {
            return this.getDataTag(false);
        }

        public Dynamic<?> getDataTagFallback() throws IOException {
            return this.getDataTag(true);
        }

        private Dynamic<?> getDataTag(boolean old) throws IOException {
            this.checkLock();
            return LevelStorageSource.readLevelDataTagFixed(old ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile(), LevelStorageSource.this.fixerUpper);
        }

        public void saveDataTag(RegistryAccess registryManager, WorldData saveProperties) {
            this.saveDataTag(registryManager, saveProperties, (CompoundTag) null);
        }

        public void saveDataTag(RegistryAccess registryManager, WorldData saveProperties, @Nullable CompoundTag nbt) {
            CompoundTag nbttagcompound1 = saveProperties.createTag(registryManager, nbt);
            CompoundTag nbttagcompound2 = new CompoundTag();

            nbttagcompound2.put("Data", nbttagcompound1);
            this.saveLevelData(nbttagcompound2);
        }

        private void saveLevelData(CompoundTag nbt) {
            Path path = this.levelDirectory.path();

            try {
                Path path1 = Files.createTempFile(path, "level", ".dat");

                NbtIo.writeCompressed(nbt, path1);
                Path path2 = this.levelDirectory.oldDataFile();
                Path path3 = this.levelDirectory.dataFile();

                Util.safeReplaceFile(path3, path1, path2);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to save level {}", path, exception);
            }

        }

        public Optional<Path> getIconFile() {
            return !this.lock.isValid() ? Optional.empty() : Optional.of(this.levelDirectory.iconFile());
        }

        public void deleteLevel() throws IOException {
            this.checkLock();
            final Path path = this.levelDirectory.lockFile();

            LevelStorageSource.LOGGER.info("Deleting level {}", this.levelId);
            int i = 1;

            while (i <= 5) {
                LevelStorageSource.LOGGER.info("Attempt {}...", i);

                try {
                    Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                        public FileVisitResult visitFile(Path path1, BasicFileAttributes basicfileattributes) throws IOException {
                            if (!path1.equals(path)) {
                                LevelStorageSource.LOGGER.debug("Deleting {}", path1);
                                Files.delete(path1);
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        public FileVisitResult postVisitDirectory(Path path1, @Nullable IOException ioexception) throws IOException {
                            if (ioexception != null) {
                                throw ioexception;
                            } else {
                                if (path1.equals(LevelStorageAccess.this.levelDirectory.path())) {
                                    LevelStorageAccess.this.lock.close();
                                    Files.deleteIfExists(path);
                                }

                                Files.delete(path1);
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    });
                    break;
                } catch (IOException ioexception) {
                    if (i >= 5) {
                        throw ioexception;
                    }

                    LevelStorageSource.LOGGER.warn("Failed to delete {}", this.levelDirectory.path(), ioexception);

                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException interruptedexception) {
                        ;
                    }

                    ++i;
                }
            }

        }

        public void renameLevel(String name) throws IOException {
            this.modifyLevelDataWithoutDatafix((nbttagcompound) -> {
                nbttagcompound.putString("LevelName", name.trim());
            });
        }

        public void renameAndDropPlayer(String name) throws IOException {
            this.modifyLevelDataWithoutDatafix((nbttagcompound) -> {
                nbttagcompound.putString("LevelName", name.trim());
                nbttagcompound.remove("Player");
            });
        }

        private void modifyLevelDataWithoutDatafix(Consumer<CompoundTag> nbtProcessor) throws IOException {
            this.checkLock();
            CompoundTag nbttagcompound = LevelStorageSource.readLevelDataTagRaw(this.levelDirectory.dataFile());

            nbtProcessor.accept(nbttagcompound.getCompound("Data"));
            this.saveLevelData(nbttagcompound);
        }

        public long makeWorldBackup() throws IOException {
            this.checkLock();
            String s = LocalDateTime.now().format(LevelStorageSource.FORMATTER);
            String s1 = s + "_" + this.levelId;
            Path path = LevelStorageSource.this.getBackupPath();

            try {
                FileUtil.createDirectoriesSafe(path);
            } catch (IOException ioexception) {
                throw new RuntimeException(ioexception);
            }

            Path path1 = path.resolve(FileUtil.findAvailableName(path, s1, ".zip"));
            final ZipOutputStream zipoutputstream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path1)));

            try {
                final Path path2 = Paths.get(this.levelId);

                Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path path3, BasicFileAttributes basicfileattributes) throws IOException {
                        if (path3.endsWith("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        } else {
                            String s2 = path2.resolve(LevelStorageAccess.this.levelDirectory.path().relativize(path3)).toString().replace('\\', '/');
                            ZipEntry zipentry = new ZipEntry(s2);

                            zipoutputstream.putNextEntry(zipentry);
                            com.google.common.io.Files.asByteSource(path3.toFile()).copyTo(zipoutputstream);
                            zipoutputstream.closeEntry();
                            return FileVisitResult.CONTINUE;
                        }
                    }
                });
            } catch (Throwable throwable) {
                try {
                    zipoutputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }

                throw throwable;
            }

            zipoutputstream.close();
            return Files.size(path1);
        }

        public boolean hasWorldData() {
            return Files.exists(this.levelDirectory.dataFile(), new LinkOption[0]) || Files.exists(this.levelDirectory.oldDataFile(), new LinkOption[0]);
        }

        public void close() throws IOException {
            this.lock.close();
        }

        public boolean restoreLevelDataFromOld() {
            return Util.safeReplaceOrMoveFile(this.levelDirectory.dataFile(), this.levelDirectory.oldDataFile(), this.levelDirectory.corruptedDataFile(LocalDateTime.now()), true);
        }

        @Nullable
        public Instant getFileModificationTime(boolean old) {
            return LevelStorageSource.getFileModificationTime(old ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile());
        }
    }
}
