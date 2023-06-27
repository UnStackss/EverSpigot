package net.minecraft.util.worldupdate;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Reference2FloatMap;
import it.unimi.dsi.fastutil.objects.Reference2FloatMaps;
import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RecreatingChunkStorage;
import net.minecraft.world.level.chunk.storage.RecreatingSimpleRegionStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

public class WorldUpgrader {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder()).setDaemon(true).build();
    private static final String NEW_DIRECTORY_PREFIX = "new_";
    static final MutableComponent STATUS_UPGRADING_POI = Component.translatable("optimizeWorld.stage.upgrading.poi");
    static final MutableComponent STATUS_FINISHED_POI = Component.translatable("optimizeWorld.stage.finished.poi");
    static final MutableComponent STATUS_UPGRADING_ENTITIES = Component.translatable("optimizeWorld.stage.upgrading.entities");
    static final MutableComponent STATUS_FINISHED_ENTITIES = Component.translatable("optimizeWorld.stage.finished.entities");
    static final MutableComponent STATUS_UPGRADING_CHUNKS = Component.translatable("optimizeWorld.stage.upgrading.chunks");
    static final MutableComponent STATUS_FINISHED_CHUNKS = Component.translatable("optimizeWorld.stage.finished.chunks");
    final Registry<LevelStem> dimensions;
    final Set<ResourceKey<Level>> levels;
    final boolean eraseCache;
    final boolean recreateRegionFiles;
    final LevelStorageSource.LevelStorageAccess levelStorage;
    private final Thread thread;
    final DataFixer dataFixer;
    volatile boolean running = true;
    private volatile boolean finished;
    volatile float progress;
    volatile int totalChunks;
    volatile int totalFiles;
    volatile int converted;
    volatile int skipped;
    final Reference2FloatMap<ResourceKey<Level>> progressMap = Reference2FloatMaps.synchronize(new Reference2FloatOpenHashMap());
    volatile Component status = Component.translatable("optimizeWorld.stage.counting");
    static final Pattern REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    final DimensionDataStorage overworldDataStorage;

    public WorldUpgrader(LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer, RegistryAccess dynamicRegistryManager, boolean eraseCache, boolean recreateRegionFiles) {
        this.dimensions = dynamicRegistryManager.registryOrThrow(Registries.LEVEL_STEM);
        this.levels = (Set) java.util.stream.Stream.of(session.dimensionType).map(Registries::levelStemToLevel).collect(Collectors.toUnmodifiableSet()); // CraftBukkit
        this.eraseCache = eraseCache;
        this.dataFixer = dataFixer;
        this.levelStorage = session;
        this.overworldDataStorage = new DimensionDataStorage(this.levelStorage.getDimensionPath(Level.OVERWORLD).resolve("data").toFile(), dataFixer, dynamicRegistryManager);
        this.recreateRegionFiles = recreateRegionFiles;
        this.thread = WorldUpgrader.THREAD_FACTORY.newThread(this::work);
        this.thread.setUncaughtExceptionHandler((thread, throwable) -> {
            WorldUpgrader.LOGGER.error("Error upgrading world", throwable);
            this.status = Component.translatable("optimizeWorld.stage.failed");
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException interruptedexception) {
            ;
        }

    }

    private void work() {
        long i = Util.getMillis();

        WorldUpgrader.LOGGER.info("Upgrading entities");
        (new WorldUpgrader.EntityUpgrader(this)).upgrade();
        WorldUpgrader.LOGGER.info("Upgrading POIs");
        (new WorldUpgrader.PoiUpgrader(this)).upgrade();
        WorldUpgrader.LOGGER.info("Upgrading blocks");
        (new WorldUpgrader.ChunkUpgrader()).upgrade();
        // Paper start - Write SavedData IO async
        try {
            this.overworldDataStorage.close();
        } catch (final IOException e) {
            LOGGER.error("Failed to close persistent world data", e);
        }
        // Paper end - Write SavedData IO async
        i = Util.getMillis() - i;
        WorldUpgrader.LOGGER.info("World optimizaton finished after {} seconds", i / 1000L);
        this.finished = true;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public Set<ResourceKey<Level>> levels() {
        return this.levels;
    }

    public float dimensionProgress(ResourceKey<Level> world) {
        return this.progressMap.getFloat(world);
    }

    public float getProgress() {
        return this.progress;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getConverted() {
        return this.converted;
    }

    public int getSkipped() {
        return this.skipped;
    }

    public Component getStatus() {
        return this.status;
    }

    static Path resolveRecreateDirectory(Path current) {
        return current.resolveSibling("new_" + current.getFileName().toString());
    }

    private class EntityUpgrader extends WorldUpgrader.SimpleRegionStorageUpgrader {

        EntityUpgrader(final WorldUpgrader worldupgrader) {
            super(DataFixTypes.ENTITY_CHUNK, "entities", WorldUpgrader.STATUS_UPGRADING_ENTITIES, WorldUpgrader.STATUS_FINISHED_ENTITIES);
        }

        @Override
        protected CompoundTag upgradeTag(SimpleRegionStorage storage, CompoundTag nbt) {
            return storage.upgradeChunkTag(nbt, -1);
        }
    }

    private class PoiUpgrader extends WorldUpgrader.SimpleRegionStorageUpgrader {

        PoiUpgrader(final WorldUpgrader worldupgrader) {
            super(DataFixTypes.POI_CHUNK, "poi", WorldUpgrader.STATUS_UPGRADING_POI, WorldUpgrader.STATUS_FINISHED_POI);
        }

        @Override
        protected CompoundTag upgradeTag(SimpleRegionStorage storage, CompoundTag nbt) {
            return storage.upgradeChunkTag(nbt, 1945);
        }
    }

    private class ChunkUpgrader extends WorldUpgrader.AbstractUpgrader<ChunkStorage> {

        ChunkUpgrader() {
            super(DataFixTypes.CHUNK, "chunk", "region", WorldUpgrader.STATUS_UPGRADING_CHUNKS, WorldUpgrader.STATUS_FINISHED_CHUNKS);
        }

        protected boolean tryProcessOnePosition(ChunkStorage storage, ChunkPos chunkPos, ResourceKey<Level> worldKey) {
            CompoundTag nbttagcompound = (CompoundTag) ((Optional) storage.read(chunkPos).join()).orElse((Object) null);

            if (nbttagcompound != null) {
                int i = ChunkStorage.getVersion(nbttagcompound);
                ChunkGenerator chunkgenerator = ((LevelStem) WorldUpgrader.this.dimensions.getOrThrow(Registries.levelToLevelStem(worldKey))).generator();
                CompoundTag nbttagcompound1 = storage.upgradeChunkTag(Registries.levelToLevelStem(worldKey), () -> { // CraftBukkit
                    return WorldUpgrader.this.overworldDataStorage;
                }, nbttagcompound, chunkgenerator.getTypeNameForDataFixer(), chunkPos, null); // CraftBukkit
                ChunkPos chunkcoordintpair1 = new ChunkPos(nbttagcompound1.getInt("xPos"), nbttagcompound1.getInt("zPos"));

                if (!chunkcoordintpair1.equals(chunkPos)) {
                    WorldUpgrader.LOGGER.warn("Chunk {} has invalid position {}", chunkPos, chunkcoordintpair1);
                }

                boolean flag = i < SharedConstants.getCurrentVersion().getDataVersion().getVersion();

                if (WorldUpgrader.this.eraseCache) {
                    flag = flag || nbttagcompound1.contains("Heightmaps");
                    nbttagcompound1.remove("Heightmaps");
                    flag = flag || nbttagcompound1.contains("isLightOn");
                    nbttagcompound1.remove("isLightOn");
                    ListTag nbttaglist = nbttagcompound1.getList("sections", 10);

                    for (int j = 0; j < nbttaglist.size(); ++j) {
                        CompoundTag nbttagcompound2 = nbttaglist.getCompound(j);

                        flag = flag || nbttagcompound2.contains("BlockLight");
                        nbttagcompound2.remove("BlockLight");
                        flag = flag || nbttagcompound2.contains("SkyLight");
                        nbttagcompound2.remove("SkyLight");
                    }
                }

                if (flag || WorldUpgrader.this.recreateRegionFiles) {
                    if (this.previousWriteFuture != null) {
                        this.previousWriteFuture.join();
                    }

                    this.previousWriteFuture = storage.write(chunkPos, nbttagcompound1);
                    return true;
                }
            }

            return false;
        }

        @Override
        protected ChunkStorage createStorage(RegionStorageInfo key, Path worldDirectory) {
            return (ChunkStorage) (WorldUpgrader.this.recreateRegionFiles ? new RecreatingChunkStorage(key.withTypeSuffix("source"), worldDirectory, key.withTypeSuffix("target"), WorldUpgrader.resolveRecreateDirectory(worldDirectory), WorldUpgrader.this.dataFixer, true) : new ChunkStorage(key, worldDirectory, WorldUpgrader.this.dataFixer, true));
        }
    }

    private abstract class SimpleRegionStorageUpgrader extends WorldUpgrader.AbstractUpgrader<SimpleRegionStorage> {

        SimpleRegionStorageUpgrader(final DataFixTypes datafixtypes, final String s, final MutableComponent ichatmutablecomponent, final MutableComponent ichatmutablecomponent1) {
            super(datafixtypes, s, s, ichatmutablecomponent, ichatmutablecomponent1);
        }

        @Override
        protected SimpleRegionStorage createStorage(RegionStorageInfo key, Path worldDirectory) {
            return (SimpleRegionStorage) (WorldUpgrader.this.recreateRegionFiles ? new RecreatingSimpleRegionStorage(key.withTypeSuffix("source"), worldDirectory, key.withTypeSuffix("target"), WorldUpgrader.resolveRecreateDirectory(worldDirectory), WorldUpgrader.this.dataFixer, true, this.dataFixType) : new SimpleRegionStorage(key, worldDirectory, WorldUpgrader.this.dataFixer, true, this.dataFixType));
        }

        protected boolean tryProcessOnePosition(SimpleRegionStorage storage, ChunkPos chunkPos, ResourceKey<Level> worldKey) {
            CompoundTag nbttagcompound = (CompoundTag) ((Optional) storage.read(chunkPos).join()).orElse((Object) null);

            if (nbttagcompound != null) {
                int i = ChunkStorage.getVersion(nbttagcompound);
                CompoundTag nbttagcompound1 = this.upgradeTag(storage, nbttagcompound);
                boolean flag = i < SharedConstants.getCurrentVersion().getDataVersion().getVersion();

                if (flag || WorldUpgrader.this.recreateRegionFiles) {
                    if (this.previousWriteFuture != null) {
                        this.previousWriteFuture.join();
                    }

                    this.previousWriteFuture = storage.write(chunkPos, nbttagcompound1);
                    return true;
                }
            }

            return false;
        }

        protected abstract CompoundTag upgradeTag(SimpleRegionStorage storage, CompoundTag nbt);
    }

    private abstract class AbstractUpgrader<T extends AutoCloseable> {

        private final MutableComponent upgradingStatus;
        private final MutableComponent finishedStatus;
        private final String type;
        private final String folderName;
        @Nullable
        protected CompletableFuture<Void> previousWriteFuture;
        protected final DataFixTypes dataFixType;

        AbstractUpgrader(final DataFixTypes datafixtypes, final String s, final String s1, final MutableComponent ichatmutablecomponent, final MutableComponent ichatmutablecomponent1) {
            this.dataFixType = datafixtypes;
            this.type = s;
            this.folderName = s1;
            this.upgradingStatus = ichatmutablecomponent;
            this.finishedStatus = ichatmutablecomponent1;
        }

        public void upgrade() {
            WorldUpgrader.this.totalFiles = 0;
            WorldUpgrader.this.totalChunks = 0;
            WorldUpgrader.this.converted = 0;
            WorldUpgrader.this.skipped = 0;
            List<WorldUpgrader.DimensionToUpgrade<T>> list = this.getDimensionsToUpgrade();

            if (WorldUpgrader.this.totalChunks != 0) {
                float f = (float) WorldUpgrader.this.totalFiles;

                WorldUpgrader.this.status = this.upgradingStatus;

                while (WorldUpgrader.this.running) {
                    boolean flag = false;
                    float f1 = 0.0F;

                    float f2;

                    for (Iterator iterator = list.iterator(); iterator.hasNext(); f1 += f2) {
                        WorldUpgrader.DimensionToUpgrade<T> worldupgrader_c = (WorldUpgrader.DimensionToUpgrade) iterator.next();
                        ResourceKey<Level> resourcekey = worldupgrader_c.dimensionKey;
                        ListIterator<WorldUpgrader.FileToUpgrade> listiterator = worldupgrader_c.files;
                        T t0 = (T) worldupgrader_c.storage; // CraftBukkit - decompile error

                        if (listiterator.hasNext()) {
                            WorldUpgrader.FileToUpgrade worldupgrader_e = (WorldUpgrader.FileToUpgrade) listiterator.next();
                            boolean flag1 = true;

                            for (Iterator iterator1 = worldupgrader_e.chunksToUpgrade.iterator(); iterator1.hasNext(); flag = true) {
                                ChunkPos chunkcoordintpair = (ChunkPos) iterator1.next();

                                flag1 = flag1 && this.processOnePosition(resourcekey, t0, chunkcoordintpair);
                            }

                            if (WorldUpgrader.this.recreateRegionFiles) {
                                if (flag1) {
                                    this.onFileFinished(worldupgrader_e.file);
                                } else {
                                    WorldUpgrader.LOGGER.error("Failed to convert region file {}", worldupgrader_e.file.getPath());
                                }
                            }
                        }

                        f2 = (float) listiterator.nextIndex() / f;
                        WorldUpgrader.this.progressMap.put(resourcekey, f2);
                    }

                    WorldUpgrader.this.progress = f1;
                    if (!flag) {
                        break;
                    }
                }

                WorldUpgrader.this.status = this.finishedStatus;
                Iterator iterator2 = list.iterator();

                while (iterator2.hasNext()) {
                    WorldUpgrader.DimensionToUpgrade<T> worldupgrader_c1 = (WorldUpgrader.DimensionToUpgrade) iterator2.next();

                    try {
                        ((AutoCloseable) worldupgrader_c1.storage).close();
                    } catch (Exception exception) {
                        WorldUpgrader.LOGGER.error("Error upgrading chunk", exception);
                    }
                }

            }
        }

        private List<WorldUpgrader.DimensionToUpgrade<T>> getDimensionsToUpgrade() {
            List<WorldUpgrader.DimensionToUpgrade<T>> list = Lists.newArrayList();
            Iterator iterator = WorldUpgrader.this.levels.iterator();

            while (iterator.hasNext()) {
                ResourceKey<Level> resourcekey = (ResourceKey) iterator.next();
                RegionStorageInfo regionstorageinfo = new RegionStorageInfo(WorldUpgrader.this.levelStorage.getLevelId(), resourcekey, this.type);
                Path path = WorldUpgrader.this.levelStorage.getDimensionPath(resourcekey).resolve(this.folderName);
                T t0 = this.createStorage(regionstorageinfo, path);
                ListIterator<WorldUpgrader.FileToUpgrade> listiterator = this.getFilesToProcess(regionstorageinfo, path);

                list.add(new WorldUpgrader.DimensionToUpgrade<>(resourcekey, t0, listiterator));
            }

            return list;
        }

        protected abstract T createStorage(RegionStorageInfo key, Path worldDirectory);

        private ListIterator<WorldUpgrader.FileToUpgrade> getFilesToProcess(RegionStorageInfo key, Path regionDirectory) {
            List<WorldUpgrader.FileToUpgrade> list = AbstractUpgrader.getAllChunkPositions(key, regionDirectory);

            WorldUpgrader.this.totalFiles += list.size();
            WorldUpgrader.this.totalChunks += list.stream().mapToInt((worldupgrader_e) -> {
                return worldupgrader_e.chunksToUpgrade.size();
            }).sum();
            return list.listIterator();
        }

        private static List<WorldUpgrader.FileToUpgrade> getAllChunkPositions(RegionStorageInfo key, Path regionDirectory) {
            File[] afile = regionDirectory.toFile().listFiles((file, s) -> {
                return s.endsWith(".mca");
            });

            if (afile == null) {
                return List.of();
            } else {
                List<WorldUpgrader.FileToUpgrade> list = Lists.newArrayList();
                File[] afile1 = afile;
                int i = afile.length;

                for (int j = 0; j < i; ++j) {
                    File file = afile1[j];
                    Matcher matcher = WorldUpgrader.REGEX.matcher(file.getName());

                    if (matcher.matches()) {
                        int k = Integer.parseInt(matcher.group(1)) << 5;
                        int l = Integer.parseInt(matcher.group(2)) << 5;
                        List<ChunkPos> list1 = Lists.newArrayList();

                        try {
                            RegionFile regionfile = new RegionFile(key, file.toPath(), regionDirectory, true);

                            try {
                                for (int i1 = 0; i1 < 32; ++i1) {
                                    for (int j1 = 0; j1 < 32; ++j1) {
                                        ChunkPos chunkcoordintpair = new ChunkPos(i1 + k, j1 + l);

                                        if (regionfile.doesChunkExist(chunkcoordintpair)) {
                                            list1.add(chunkcoordintpair);
                                        }
                                    }
                                }

                                if (!list1.isEmpty()) {
                                    list.add(new WorldUpgrader.FileToUpgrade(regionfile, list1));
                                }
                            } catch (Throwable throwable) {
                                try {
                                    regionfile.close();
                                } catch (Throwable throwable1) {
                                    throwable.addSuppressed(throwable1);
                                }

                                throw throwable;
                            }

                            regionfile.close();
                        } catch (Throwable throwable2) {
                            WorldUpgrader.LOGGER.error("Failed to read chunks from region file {}", file.toPath(), throwable2);
                        }
                    }
                }

                return list;
            }
        }

        private boolean processOnePosition(ResourceKey<Level> worldKey, T storage, ChunkPos chunkPos) {
            boolean flag = false;

            try {
                flag = this.tryProcessOnePosition(storage, chunkPos, worldKey);
            } catch (CompletionException | ReportedException reportedexception) {
                Throwable throwable = reportedexception.getCause();

                if (!(throwable instanceof IOException)) {
                    throw reportedexception;
                }

                WorldUpgrader.LOGGER.error("Error upgrading chunk {}", chunkPos, throwable);
            }

            if (flag) {
                ++WorldUpgrader.this.converted;
            } else {
                ++WorldUpgrader.this.skipped;
            }

            return flag;
        }

        protected abstract boolean tryProcessOnePosition(T storage, ChunkPos chunkPos, ResourceKey<Level> worldKey);

        private void onFileFinished(RegionFile regionFile) {
            if (WorldUpgrader.this.recreateRegionFiles) {
                if (this.previousWriteFuture != null) {
                    this.previousWriteFuture.join();
                }

                Path path = regionFile.getPath();
                Path path1 = path.getParent();
                Path path2 = WorldUpgrader.resolveRecreateDirectory(path1).resolve(path.getFileName().toString());

                try {
                    if (path2.toFile().exists()) {
                        Files.delete(path);
                        Files.move(path2, path);
                    } else {
                        WorldUpgrader.LOGGER.error("Failed to replace an old region file. New file {} does not exist.", path2);
                    }
                } catch (IOException ioexception) {
                    WorldUpgrader.LOGGER.error("Failed to replace an old region file", ioexception);
                }

            }
        }
    }

    static record FileToUpgrade(RegionFile file, List<ChunkPos> chunksToUpgrade) {

    }

    static record DimensionToUpgrade<T>(ResourceKey<Level> dimensionKey, T storage, ListIterator<WorldUpgrader.FileToUpgrade> files) {

    }
}
