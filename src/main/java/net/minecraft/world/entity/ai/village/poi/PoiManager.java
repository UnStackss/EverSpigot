package net.minecraft.world.entity.ai.village.poi;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

public class PoiManager extends SectionStorage<PoiSection> implements ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiManager { // Paper - rewrite chunk system
    public static final int MAX_VILLAGE_DISTANCE = 6;
    public static final int VILLAGE_SECTION_SIZE = 1;
    private final PoiManager.DistanceTracker distanceTracker;
    private final LongSet loadedChunks = new LongOpenHashSet();

    // Paper start - rewrite chunk system
    private final net.minecraft.server.level.ServerLevel world;

    // the vanilla tracker needs to be replaced because it does not support level removes, and we need level removes
    // to support poi unloading
    private final ca.spottedleaf.moonrise.common.misc.Delayed26WayDistancePropagator3D villageDistanceTracker = new ca.spottedleaf.moonrise.common.misc.Delayed26WayDistancePropagator3D();

    private static final int POI_DATA_SOURCE = 7;

    private static int convertBetweenLevels(final int level) {
        return POI_DATA_SOURCE - level;
    }

    private void updateDistanceTracking(long section) {
        if (this.isVillageCenter(section)) {
            this.villageDistanceTracker.setSource(section, POI_DATA_SOURCE);
        } else {
            this.villageDistanceTracker.removeSource(section);
        }
    }

    @Override
    public Optional<PoiSection> get(final long pos) {
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);

        return ret == null ? Optional.empty() : ret.getSectionForVanilla(chunkY);
    }

    @Override
    public Optional<PoiSection> getOrLoad(final long pos) {
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;

        if (chunkY >= ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(this.world) && chunkY <= ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(this.world)) {
            final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
            if (ret != null) {
                return ret.getSectionForVanilla(chunkY);
            } else {
                return manager.loadPoiChunk(chunkX, chunkZ).getSectionForVanilla(chunkY);
            }
        }
        // retain vanilla behavior: do not load section if out of bounds!
        return Optional.empty();
    }

    @Override
    protected PoiSection getOrCreate(final long pos) {
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkY = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionY(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);

        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Accessing poi chunk off-main");

        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;

        final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk ret = manager.getPoiChunkIfLoaded(chunkX, chunkZ, true);
        if (ret != null) {
            return ret.getOrCreateSection(chunkY);
        } else {
            return manager.loadPoiChunk(chunkX, chunkZ).getOrCreateSection(chunkY);
        }
    }

    @Override
    public final net.minecraft.server.level.ServerLevel moonrise$getWorld() {
        return this.world;
    }

    @Override
    public final void moonrise$onUnload(final long coordinate) { // Paper - rewrite chunk system
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkX(coordinate);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkZ(coordinate);
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Unloading poi chunk off-main");
        for (int section = this.levelHeightAccessor.getMinSection(); section < this.levelHeightAccessor.getMaxSection(); ++section) {
            final long sectionPos = SectionPos.asLong(chunkX, section, chunkZ);
            this.updateDistanceTracking(sectionPos);
        }
    }

    @Override
    public final void moonrise$loadInPoiChunk(final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk poiChunk) {
        final int chunkX = poiChunk.chunkX;
        final int chunkZ = poiChunk.chunkZ;
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.world, chunkX, chunkZ, "Loading poi chunk off-main");
        for (int sectionY = this.levelHeightAccessor.getMinSection(); sectionY < this.levelHeightAccessor.getMaxSection(); ++sectionY) {
            final PoiSection section = poiChunk.getSection(sectionY);
            if (section != null && !((ca.spottedleaf.moonrise.patches.chunk_system.level.poi.ChunkSystemPoiSection)section).moonrise$isEmpty()) {
                this.onSectionLoad(SectionPos.asLong(chunkX, sectionY, chunkZ));
            }
        }
    }

    @Override
    public final void moonrise$checkConsistency(final net.minecraft.world.level.chunk.ChunkAccess chunk) {
        final int chunkX = chunk.getPos().x;
        final int chunkZ = chunk.getPos().z;

        final int minY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(chunk);
        final int maxY = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(chunk);
        final LevelChunkSection[] sections = chunk.getSections();
        for (int section = minY; section <= maxY; ++section) {
            this.checkConsistencyWithBlocks(SectionPos.of(chunkX, section, chunkZ), sections[section - minY]);
        }
    }

    @Override
    public final void moonrise$close() throws java.io.IOException {}

    @Override
    public final net.minecraft.nbt.CompoundTag moonrise$read(final int chunkX, final int chunkZ) throws java.io.IOException {
        if (!ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.isRegionFileThread()) {
            return ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.loadData(
                this.world, chunkX, chunkZ, ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.RegionFileType.POI_DATA,
                ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.getIOBlockingPriorityForCurrentThread()
            );
        }
        return this.moonrise$getRegionStorage().read(new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public final void moonrise$write(final int chunkX, final int chunkZ, final net.minecraft.nbt.CompoundTag data) throws java.io.IOException {
        if (!ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.isRegionFileThread()) {
            ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.scheduleSave(this.world, chunkX, chunkZ, data, ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.RegionFileType.POI_DATA);
            return;
        }
        this.moonrise$getRegionStorage().write(new ChunkPos(chunkX, chunkZ), data);
    }
    // Paper end - rewrite chunk system

    public PoiManager(
        RegionStorageInfo storageKey,
        Path directory,
        DataFixer dataFixer,
        boolean dsync,
        RegistryAccess registryManager,
        ChunkIOErrorReporter errorHandler,
        LevelHeightAccessor world
    ) {
        super(
            new SimpleRegionStorage(storageKey, directory, dataFixer, dsync, DataFixTypes.POI_CHUNK),
            PoiSection::codec,
            PoiSection::new,
            registryManager,
            errorHandler,
            world
        );
        this.distanceTracker = new PoiManager.DistanceTracker();
        this.world = (net.minecraft.server.level.ServerLevel)world; // Paper - rewrite chunk system
    }

    public void add(BlockPos pos, Holder<PoiType> type) {
        this.getOrCreate(SectionPos.asLong(pos)).add(pos, type);
    }

    public void remove(BlockPos pos) {
        this.getOrLoad(SectionPos.asLong(pos)).ifPresent(poiSet -> poiSet.remove(pos));
    }

    public long getCountInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        return this.getInRange(typePredicate, pos, radius, occupationStatus).count();
    }

    public boolean existsAtPosition(ResourceKey<PoiType> type, BlockPos pos) {
        return this.exists(pos, entry -> entry.is(type));
    }

    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        int i = Math.floorDiv(radius, 16) + 1;
        return ChunkPos.rangeClosed(new ChunkPos(pos), i).flatMap(chunkPos -> this.getInChunk(typePredicate, chunkPos, occupationStatus)).filter(poi -> {
            BlockPos blockPos2 = poi.getPos();
            return Math.abs(blockPos2.getX() - pos.getX()) <= radius && Math.abs(blockPos2.getZ() - pos.getZ()) <= radius;
        });
    }

    public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        int i = radius * radius;
        return this.getInSquare(typePredicate, pos, radius, occupationStatus).filter(poi -> poi.getPos().distSqr(pos) <= (double)i);
    }

    @VisibleForDebug
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> typePredicate, ChunkPos chunkPos, PoiManager.Occupancy occupationStatus) {
        return IntStream.range(this.levelHeightAccessor.getMinSection(), this.levelHeightAccessor.getMaxSection())
            .boxed()
            .map(integer -> this.getOrLoad(SectionPos.of(chunkPos, integer).asLong()))
            .filter(Optional::isPresent)
            .flatMap(optional -> optional.get().getRecords(typePredicate, occupationStatus));
    }

    public Stream<BlockPos> findAll(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus
    ) {
        return this.getInRange(typePredicate, pos, radius, occupationStatus).map(PoiRecord::getPos).filter(posPredicate);
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus
    ) {
        return this.getInRange(typePredicate, pos, radius, occupationStatus)
            .filter(poi -> posPredicate.test(poi.getPos()))
            .map(poi -> Pair.of(poi.getPoiType(), poi.getPos()));
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus
    ) {
        return this.findAllWithType(typePredicate, posPredicate, pos, radius, occupationStatus)
            .sorted(Comparator.comparingDouble(pair -> pair.getSecond().distSqr(pos)));
    }

    public Optional<BlockPos> find(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus
    ) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findAnyPoiPosition(this, typePredicate, posPredicate, pos, radius, occupationStatus, false);
        return Optional.ofNullable(ret);
        // Paper end
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findClosestPoiDataPosition(this, typePredicate, null, pos, radius, radius * radius, occupationStatus, false);
        return Optional.ofNullable(ret);
        // Paper end - re-route to faster logic
    }

    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(
        Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus
    ) {
        // Paper start - re-route to faster logic
        return Optional.ofNullable(io.papermc.paper.util.PoiAccess.findClosestPoiDataTypeAndPosition(
            this, typePredicate, null, pos, radius, radius * radius, occupationStatus, false
        ));
        // Paper end - re-route to faster logic
    }

    public Optional<BlockPos> findClosest(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int radius, PoiManager.Occupancy occupationStatus
    ) {
        // Paper start - re-route to faster logic
        BlockPos ret = io.papermc.paper.util.PoiAccess.findClosestPoiDataPosition(this, typePredicate, posPredicate, pos, radius, radius * radius, occupationStatus, false);
        return Optional.ofNullable(ret);
        // Paper end - re-route to faster logic
    }

    public Optional<BlockPos> take(Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> biPredicate, BlockPos pos, int radius) {
        // Paper start - re-route to faster logic
        final @javax.annotation.Nullable PoiRecord closest = io.papermc.paper.util.PoiAccess.findClosestPoiDataRecord(
            this, typePredicate, biPredicate, pos, radius, radius * radius, Occupancy.HAS_SPACE, false
        );
        return Optional.ofNullable(closest)
            // Paper end - re-route to faster logic
            .map(poi -> {
                poi.acquireTicket();
                return poi.getPos();
            });
    }

    public Optional<BlockPos> getRandom(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> positionPredicate,
        PoiManager.Occupancy occupationStatus,
        BlockPos pos,
        int radius,
        RandomSource random
    ) {
        // Paper start - re-route to faster logic
        List<PoiRecord> list = new java.util.ArrayList<>();
        io.papermc.paper.util.PoiAccess.findAnyPoiRecords(
            this, typePredicate, positionPredicate, pos, radius, occupationStatus, false, Integer.MAX_VALUE, list
        );

        // the old method shuffled the list and then tried to find the first element in it that
        // matched positionPredicate, however we moved positionPredicate into the poi search. This means we can avoid a
        // shuffle entirely, and just pick a random element from list
        if (list.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(list.get(random.nextInt(list.size())).getPos());
        // Paper end - re-route to faster logic
    }

    public boolean release(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos))
            .map(poiSet -> poiSet.release(pos))
            .orElseThrow(() -> Util.pauseInIde(new IllegalStateException("POI never registered at " + pos)));
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> predicate) {
        return this.getOrLoad(SectionPos.asLong(pos)).map(poiSet -> poiSet.exists(pos, predicate)).orElse(false);
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).flatMap(poiSet -> poiSet.getType(pos));
    }

    @Deprecated
    @VisibleForDebug
    public int getFreeTickets(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).map(poiSet -> poiSet.getFreeTickets(pos)).orElse(0);
    }

    public int sectionsToVillage(SectionPos pos) {
        this.villageDistanceTracker.propagateUpdates(); // Paper - rewrite chunk system
        return convertBetweenLevels(this.villageDistanceTracker.getLevel(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionKey(pos))); // Paper - rewrite chunk system
    }

    boolean isVillageCenter(long pos) {
        Optional<PoiSection> optional = this.get(pos);
        return optional != null
            && optional.<Boolean>map(
                    poiSet -> poiSet.getRecords(entry -> entry.is(PoiTypeTags.VILLAGE), PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent()
                )
                .orElse(false);
    }

    @Override
    public void tick(BooleanSupplier shouldKeepTicking) {
        this.villageDistanceTracker.propagateUpdates(); // Paper - rewrite chunk system
    }

    @Override
    public void setDirty(long pos) { // Paper - public
        // Paper start - rewrite chunk system
        final int chunkX = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionX(pos);
        final int chunkZ = ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkSectionZ(pos);
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager manager = ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel)this.world).moonrise$getChunkTaskScheduler().chunkHolderManager;
        final ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk chunk = manager.getPoiChunkIfLoaded(chunkX, chunkZ, false);
        if (chunk != null) {
            chunk.setDirty(true);
        }
        this.updateDistanceTracking(pos);
        // Paper end - rewrite chunk system
    }

    @Override
    protected void onSectionLoad(long pos) {
        this.updateDistanceTracking(pos); // Paper - rewrite chunk system
    }

    public void checkConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection chunkSection) {
        Util.ifElse(this.getOrLoad(sectionPos.asLong()), poiSet -> poiSet.refresh(populator -> {
                if (mayHavePoi(chunkSection)) {
                    this.updateFromSection(chunkSection, sectionPos, populator);
                }
            }), () -> {
            if (mayHavePoi(chunkSection)) {
                PoiSection poiSection = this.getOrCreate(sectionPos.asLong());
                this.updateFromSection(chunkSection, sectionPos, poiSection::add);
            }
        });
    }

    private static boolean mayHavePoi(LevelChunkSection chunkSection) {
        return chunkSection.maybeHas(PoiTypes::hasPoi);
    }

    private void updateFromSection(LevelChunkSection chunkSection, SectionPos sectionPos, BiConsumer<BlockPos, Holder<PoiType>> populator) {
        sectionPos.blocksInside()
            .forEach(
                pos -> {
                    BlockState blockState = chunkSection.getBlockState(
                        SectionPos.sectionRelative(pos.getX()), SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ())
                    );
                    PoiTypes.forState(blockState).ifPresent(poiType -> populator.accept(pos, (Holder<PoiType>)poiType));
                }
            );
    }

    public void ensureLoadedAndValid(LevelReader world, BlockPos pos, int radius) {
        SectionPos.aroundChunk(new ChunkPos(pos), Math.floorDiv(radius, 16), this.levelHeightAccessor.getMinSection(), this.levelHeightAccessor.getMaxSection())
            .map(sectionPos -> Pair.of(sectionPos, this.getOrLoad(sectionPos.asLong())))
            .filter(pair -> !pair.getSecond().map(PoiSection::isValid).orElse(false))
            .map(pair -> pair.getFirst().chunk())
            // Paper - rewrite chunk system
            .forEach(chunkPos -> world.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY));
    }

    final class DistanceTracker extends SectionTracker {
        private final Long2ByteMap levels = new Long2ByteOpenHashMap();

        protected DistanceTracker() {
            super(7, 16, 256);
            this.levels.defaultReturnValue((byte)7);
        }

        @Override
        protected int getLevelFromSource(long id) {
            return PoiManager.this.isVillageCenter(id) ? 0 : 7;
        }

        @Override
        protected int getLevel(long id) {
            return this.levels.get(id);
        }

        @Override
        protected void setLevel(long id, int level) {
            if (level > 6) {
                this.levels.remove(id);
            } else {
                this.levels.put(id, (byte)level);
            }
        }

        public void runAllUpdates() {
            super.runUpdates(Integer.MAX_VALUE);
        }
    }

    public static enum Occupancy {
        HAS_SPACE(PoiRecord::hasSpace),
        IS_OCCUPIED(PoiRecord::isOccupied),
        ANY(poi -> true);

        private final Predicate<? super PoiRecord> test;

        private Occupancy(final Predicate<? super PoiRecord> predicate) {
            this.test = predicate;
        }

        public Predicate<? super PoiRecord> getTest() {
            return this.test;
        }
    }
}
