package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.slf4j.Logger;

public class LevelChunk extends ChunkAccess {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
        @Override
        public void tick() {}

        @Override
        public boolean isRemoved() {
            return true;
        }

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public String getType() {
            return "<null>";
        }
    };
    private final Map<BlockPos, LevelChunk.RebindableTickingBlockEntityWrapper> tickersInLevel;
    public boolean loaded;
    public final ServerLevel level; // CraftBukkit - type
    @Nullable
    private Supplier<FullChunkStatus> fullStatus;
    @Nullable
    private LevelChunk.PostLoadProcessor postLoad;
    private final Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;
    private final LevelChunkTicks<Block> blockTicks;
    private final LevelChunkTicks<Fluid> fluidTicks;

    public LevelChunk(Level world, ChunkPos pos) {
        this(world, pos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, (LevelChunkSection[]) null, (LevelChunk.PostLoadProcessor) null, (BlendingData) null);
    }

    public LevelChunk(Level world, ChunkPos pos, UpgradeData upgradeData, LevelChunkTicks<Block> blockTickScheduler, LevelChunkTicks<Fluid> fluidTickScheduler, long inhabitedTime, @Nullable LevelChunkSection[] sectionArrayInitializer, @Nullable LevelChunk.PostLoadProcessor entityLoader, @Nullable BlendingData blendingData) {
        super(pos, upgradeData, world, world.registryAccess().registryOrThrow(Registries.BIOME), inhabitedTime, sectionArrayInitializer, blendingData);
        this.tickersInLevel = Maps.newHashMap();
        this.level = (ServerLevel) world; // CraftBukkit - type
        this.gameEventListenerRegistrySections = new Int2ObjectOpenHashMap();
        Heightmap.Types[] aheightmap_type = Heightmap.Types.values();
        int j = aheightmap_type.length;

        for (int k = 0; k < j; ++k) {
            Heightmap.Types heightmap_type = aheightmap_type[k];

            if (ChunkStatus.FULL.heightmapsAfter().contains(heightmap_type)) {
                this.heightmaps.put(heightmap_type, new Heightmap(this, heightmap_type));
            }
        }

        this.postLoad = entityLoader;
        this.blockTicks = blockTickScheduler;
        this.fluidTicks = fluidTickScheduler;
    }

    // CraftBukkit start
    public boolean mustNotSave;
    public boolean needsDecoration;
    // CraftBukkit end

    public LevelChunk(ServerLevel world, ProtoChunk protoChunk, @Nullable LevelChunk.PostLoadProcessor entityLoader) {
        this(world, protoChunk.getPos(), protoChunk.getUpgradeData(), protoChunk.unpackBlockTicks(), protoChunk.unpackFluidTicks(), protoChunk.getInhabitedTime(), protoChunk.getSections(), entityLoader, protoChunk.getBlendingData());
        Iterator iterator = protoChunk.getBlockEntities().values().iterator();

        while (iterator.hasNext()) {
            BlockEntity tileentity = (BlockEntity) iterator.next();

            this.setBlockEntity(tileentity);
        }

        this.pendingBlockEntities.putAll(protoChunk.getBlockEntityNbts());

        for (int i = 0; i < protoChunk.getPostProcessing().length; ++i) {
            this.postProcessing[i] = protoChunk.getPostProcessing()[i];
        }

        this.setAllStarts(protoChunk.getAllStarts());
        this.setAllReferences(protoChunk.getAllReferences());
        iterator = protoChunk.getHeightmaps().iterator();

        while (iterator.hasNext()) {
            Entry<Heightmap.Types, Heightmap> entry = (Entry) iterator.next();

            if (ChunkStatus.FULL.heightmapsAfter().contains(entry.getKey())) {
                this.setHeightmap((Heightmap.Types) entry.getKey(), ((Heightmap) entry.getValue()).getRawData());
            }
        }

        this.skyLightSources = protoChunk.skyLightSources;
        this.setLightCorrect(protoChunk.isLightCorrect());
        this.unsaved = true;
        this.needsDecoration = true; // CraftBukkit
        // CraftBukkit start
        this.persistentDataContainer = protoChunk.persistentDataContainer; // SPIGOT-6814: copy PDC to account for 1.17 to 1.18 chunk upgrading.
        // CraftBukkit end
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public ChunkAccess.TicksToSave getTicksForSerialization() {
        return new ChunkAccess.TicksToSave(this.blockTicks, this.fluidTicks);
    }

    @Override
    public GameEventListenerRegistry getListenerRegistry(int ySectionCoord) {
        Level world = this.level;

        if (world instanceof ServerLevel worldserver) {
            return (GameEventListenerRegistry) this.gameEventListenerRegistrySections.computeIfAbsent(ySectionCoord, (j) -> {
                return new EuclideanGameEventListenerRegistry(worldserver, ySectionCoord, this::removeGameEventListenerRegistry);
            });
        } else {
            return super.getListenerRegistry(ySectionCoord);
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        if (this.level.isDebug()) {
            BlockState iblockdata = null;

            if (j == 60) {
                iblockdata = Blocks.BARRIER.defaultBlockState();
            }

            if (j == 70) {
                iblockdata = DebugLevelSource.getBlockStateFor(i, k);
            }

            return iblockdata == null ? Blocks.AIR.defaultBlockState() : iblockdata;
        } else {
            try {
                int l = this.getSectionIndex(j);

                if (l >= 0 && l < this.sections.length) {
                    LevelChunkSection chunksection = this.sections[l];

                    if (!chunksection.hasOnlyAir()) {
                        return chunksection.getBlockState(i & 15, j & 15, k & 15);
                    }
                }

                return Blocks.AIR.defaultBlockState();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting block state");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being got");

                crashreportsystemdetails.setDetail("Location", () -> {
                    return CrashReportCategory.formatLocation(this, i, j, k);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }

    public FluidState getFluidState(int x, int y, int z) {
        try {
            int l = this.getSectionIndex(y);

            if (l >= 0 && l < this.sections.length) {
                LevelChunkSection chunksection = this.sections[l];

                if (!chunksection.hasOnlyAir()) {
                    return chunksection.getFluidState(x & 15, y & 15, z & 15);
                }
            }

            return Fluids.EMPTY.defaultFluidState();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting fluid state");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being got");

            crashreportsystemdetails.setDetail("Location", () -> {
                return CrashReportCategory.formatLocation(this, x, y, z);
            });
            throw new ReportedException(crashreport);
        }
    }

    // CraftBukkit start
    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        return this.setBlockState(pos, state, moved, true);
    }

    @Nullable
    public BlockState setBlockState(BlockPos blockposition, BlockState iblockdata, boolean flag, boolean doPlace) {
        // CraftBukkit end
        int i = blockposition.getY();
        LevelChunkSection chunksection = this.getSection(this.getSectionIndex(i));
        boolean flag1 = chunksection.hasOnlyAir();

        if (flag1 && iblockdata.isAir()) {
            return null;
        } else {
            int j = blockposition.getX() & 15;
            int k = i & 15;
            int l = blockposition.getZ() & 15;
            BlockState iblockdata1 = chunksection.setBlockState(j, k, l, iblockdata);

            if (iblockdata1 == iblockdata) {
                return null;
            } else {
                Block block = iblockdata.getBlock();

                ((Heightmap) this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING)).update(j, i, l, iblockdata);
                ((Heightmap) this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)).update(j, i, l, iblockdata);
                ((Heightmap) this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR)).update(j, i, l, iblockdata);
                ((Heightmap) this.heightmaps.get(Heightmap.Types.WORLD_SURFACE)).update(j, i, l, iblockdata);
                boolean flag2 = chunksection.hasOnlyAir();

                if (flag1 != flag2) {
                    this.level.getChunkSource().getLightEngine().updateSectionStatus(blockposition, flag2);
                }

                if (LightEngine.hasDifferentLightProperties(this, blockposition, iblockdata1, iblockdata)) {
                    ProfilerFiller gameprofilerfiller = this.level.getProfiler();

                    gameprofilerfiller.push("updateSkyLightSources");
                    this.skyLightSources.update(this, j, i, l);
                    gameprofilerfiller.popPush("queueCheckLight");
                    this.level.getChunkSource().getLightEngine().checkBlock(blockposition);
                    gameprofilerfiller.pop();
                }

                boolean flag3 = iblockdata1.hasBlockEntity();

                if (!this.level.isClientSide) {
                    iblockdata1.onRemove(this.level, blockposition, iblockdata, flag);
                } else if (!iblockdata1.is(block) && flag3) {
                    this.removeBlockEntity(blockposition);
                }

                if (!chunksection.getBlockState(j, k, l).is(block)) {
                    return null;
                } else {
                    // CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer. Prevents blocks such as TNT from activating when cancelled.
                    if (!this.level.isClientSide && doPlace && (!this.level.captureBlockStates || block instanceof net.minecraft.world.level.block.BaseEntityBlock)) {
                        iblockdata.onPlace(this.level, blockposition, iblockdata1, flag);
                    }

                    if (iblockdata.hasBlockEntity()) {
                        BlockEntity tileentity = this.getBlockEntity(blockposition, LevelChunk.EntityCreationType.CHECK);

                        if (tileentity != null && !tileentity.isValidBlockState(iblockdata)) {
                            this.removeBlockEntity(blockposition);
                            tileentity = null;
                        }

                        if (tileentity == null) {
                            tileentity = ((EntityBlock) block).newBlockEntity(blockposition, iblockdata);
                            if (tileentity != null) {
                                this.addAndRegisterBlockEntity(tileentity);
                            }
                        } else {
                            tileentity.setBlockState(iblockdata);
                            this.updateBlockEntityTicker(tileentity);
                        }
                    }

                    this.unsaved = true;
                    return iblockdata1;
                }
            }
        }
    }

    /** @deprecated */
    @Deprecated
    @Override
    public void addEntity(Entity entity) {}

    @Nullable
    private BlockEntity createBlockEntity(BlockPos pos) {
        BlockState iblockdata = this.getBlockState(pos);

        return !iblockdata.hasBlockEntity() ? null : ((EntityBlock) iblockdata.getBlock()).newBlockEntity(pos, iblockdata);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType) {
        // CraftBukkit start
        BlockEntity tileentity = this.level.capturedTileEntities.get(pos);
        if (tileentity == null) {
            tileentity = (BlockEntity) this.blockEntities.get(pos);
        }
        // CraftBukkit end

        if (tileentity == null) {
            CompoundTag nbttagcompound = (CompoundTag) this.pendingBlockEntities.remove(pos);

            if (nbttagcompound != null) {
                BlockEntity tileentity1 = this.promotePendingBlockEntity(pos, nbttagcompound);

                if (tileentity1 != null) {
                    return tileentity1;
                }
            }
        }

        if (tileentity == null) {
            if (creationType == LevelChunk.EntityCreationType.IMMEDIATE) {
                tileentity = this.createBlockEntity(pos);
                if (tileentity != null) {
                    this.addAndRegisterBlockEntity(tileentity);
                }
            }
        } else if (tileentity.isRemoved()) {
            this.blockEntities.remove(pos);
            return null;
        }

        return tileentity;
    }

    public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
        this.setBlockEntity(blockEntity);
        if (this.isInLevel()) {
            Level world = this.level;

            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                this.addGameEventListener(blockEntity, worldserver);
            }

            this.updateBlockEntityTicker(blockEntity);
        }

    }

    private boolean isInLevel() {
        return this.loaded || this.level.isClientSide();
    }

    boolean isTicking(BlockPos pos) {
        if (!this.level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        } else {
            Level world = this.level;

            if (!(world instanceof ServerLevel)) {
                return true;
            } else {
                ServerLevel worldserver = (ServerLevel) world;

                return this.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && worldserver.areEntitiesLoaded(ChunkPos.asLong(pos));
            }
        }
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockposition = blockEntity.getBlockPos();
        BlockState iblockdata = this.getBlockState(blockposition);

        if (!iblockdata.hasBlockEntity()) {
            LevelChunk.LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", new Object[]{blockEntity, blockposition, iblockdata});
            new Exception().printStackTrace(); // CraftBukkit
        } else {
            BlockState iblockdata1 = blockEntity.getBlockState();

            if (iblockdata != iblockdata1) {
                if (!blockEntity.getType().isValid(iblockdata)) {
                    LevelChunk.LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", new Object[]{blockEntity, blockposition, iblockdata});
                    return;
                }

                if (iblockdata.getBlock() != iblockdata1.getBlock()) {
                    LevelChunk.LOGGER.warn("Block state mismatch on block entity {} in position {}, {} != {}, updating", new Object[]{blockEntity, blockposition, iblockdata, iblockdata1});
                }

                blockEntity.setBlockState(iblockdata);
            }

            blockEntity.setLevel(this.level);
            blockEntity.clearRemoved();
            BlockEntity tileentity1 = (BlockEntity) this.blockEntities.put(blockposition.immutable(), blockEntity);

            if (tileentity1 != null && tileentity1 != blockEntity) {
                tileentity1.setRemoved();
            }

        }
    }

    @Nullable
    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registryLookup) {
        BlockEntity tileentity = this.getBlockEntity(pos);
        CompoundTag nbttagcompound;

        if (tileentity != null && !tileentity.isRemoved()) {
            nbttagcompound = tileentity.saveWithFullMetadata(this.level.registryAccess());
            nbttagcompound.putBoolean("keepPacked", false);
            return nbttagcompound;
        } else {
            nbttagcompound = (CompoundTag) this.pendingBlockEntities.get(pos);
            if (nbttagcompound != null) {
                nbttagcompound = nbttagcompound.copy();
                nbttagcompound.putBoolean("keepPacked", true);
            }

            return nbttagcompound;
        }
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        if (this.isInLevel()) {
            BlockEntity tileentity = (BlockEntity) this.blockEntities.remove(pos);

            // CraftBukkit start - SPIGOT-5561: Also remove from pending map
            if (!this.pendingBlockEntities.isEmpty()) {
                this.pendingBlockEntities.remove(pos);
            }
            // CraftBukkit end

            if (tileentity != null) {
                Level world = this.level;

                if (world instanceof ServerLevel) {
                    ServerLevel worldserver = (ServerLevel) world;

                    this.removeGameEventListener(tileentity, worldserver);
                }

                tileentity.setRemoved();
            }
        }

        this.removeBlockEntityTicker(pos);
    }

    private <T extends BlockEntity> void removeGameEventListener(T blockEntity, ServerLevel world) {
        Block block = blockEntity.getBlockState().getBlock();

        if (block instanceof EntityBlock) {
            GameEventListener gameeventlistener = ((EntityBlock) block).getListener(world, blockEntity);

            if (gameeventlistener != null) {
                int i = SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY());
                GameEventListenerRegistry gameeventlistenerregistry = this.getListenerRegistry(i);

                gameeventlistenerregistry.unregister(gameeventlistener);
            }
        }

    }

    private void removeGameEventListenerRegistry(int ySectionCoord) {
        this.gameEventListenerRegistrySections.remove(ySectionCoord);
    }

    private void removeBlockEntityTicker(BlockPos pos) {
        LevelChunk.RebindableTickingBlockEntityWrapper chunk_d = (LevelChunk.RebindableTickingBlockEntityWrapper) this.tickersInLevel.remove(pos);

        if (chunk_d != null) {
            chunk_d.rebind(LevelChunk.NULL_TICKER);
        }

    }

    public void runPostLoad() {
        if (this.postLoad != null) {
            this.postLoad.run(this);
            this.postLoad = null;
        }

    }

    // CraftBukkit start
    public void loadCallback() {
        org.bukkit.Server server = this.level.getCraftServer();
        if (server != null) {
            /*
             * If it's a new world, the first few chunks are generated inside
             * the World constructor. We can't reliably alter that, so we have
             * no way of creating a CraftWorld/CraftServer at that point.
             */
            org.bukkit.Chunk bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
            server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(bukkitChunk, this.needsDecoration));

            if (this.needsDecoration) {
                this.needsDecoration = false;
                java.util.Random random = new java.util.Random();
                random.setSeed(this.level.getSeed());
                long xRand = random.nextLong() / 2L * 2L + 1L;
                long zRand = random.nextLong() / 2L * 2L + 1L;
                random.setSeed((long) this.chunkPos.x * xRand + (long) this.chunkPos.z * zRand ^ this.level.getSeed());

                org.bukkit.World world = this.level.getWorld();
                if (world != null) {
                    this.level.populating = true;
                    try {
                        for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                            populator.populate(world, random, bukkitChunk);
                        }
                    } finally {
                        this.level.populating = false;
                    }
                }
                server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkPopulateEvent(bukkitChunk));
            }
        }
    }

    public void unloadCallback() {
        org.bukkit.Server server = this.level.getCraftServer();
        org.bukkit.Chunk bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
        org.bukkit.event.world.ChunkUnloadEvent unloadEvent = new org.bukkit.event.world.ChunkUnloadEvent(bukkitChunk, this.isUnsaved());
        server.getPluginManager().callEvent(unloadEvent);
        // note: saving can be prevented, but not forced if no saving is actually required
        this.mustNotSave = !unloadEvent.isSaveChunk();
    }

    @Override
    public boolean isUnsaved() {
        return super.isUnsaved() && !this.mustNotSave;
    }
    // CraftBukkit end

    public boolean isEmpty() {
        return false;
    }

    public void replaceWithPacketData(FriendlyByteBuf buf, CompoundTag nbt, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer) {
        this.clearAllBlockEntities();
        LevelChunkSection[] achunksection = this.sections;
        int i = achunksection.length;

        int j;

        for (j = 0; j < i; ++j) {
            LevelChunkSection chunksection = achunksection[j];

            chunksection.read(buf);
        }

        Heightmap.Types[] aheightmap_type = Heightmap.Types.values();

        i = aheightmap_type.length;

        for (j = 0; j < i; ++j) {
            Heightmap.Types heightmap_type = aheightmap_type[j];
            String s = heightmap_type.getSerializationKey();

            if (nbt.contains(s, 12)) {
                this.setHeightmap(heightmap_type, nbt.getLongArray(s));
            }
        }

        this.initializeLightSources();
        consumer.accept((blockposition, tileentitytypes, nbttagcompound1) -> {
            BlockEntity tileentity = this.getBlockEntity(blockposition, LevelChunk.EntityCreationType.IMMEDIATE);

            if (tileentity != null && nbttagcompound1 != null && tileentity.getType() == tileentitytypes) {
                tileentity.loadWithComponents(nbttagcompound1, this.level.registryAccess());
            }

        });
    }

    public void replaceBiomes(FriendlyByteBuf buf) {
        LevelChunkSection[] achunksection = this.sections;
        int i = achunksection.length;

        for (int j = 0; j < i; ++j) {
            LevelChunkSection chunksection = achunksection[j];

            chunksection.readBiomes(buf);
        }

    }

    public void setLoaded(boolean loadedToWorld) {
        this.loaded = loadedToWorld;
    }

    public Level getLevel() {
        return this.level;
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void postProcessGeneration() {
        ChunkPos chunkcoordintpair = this.getPos();

        for (int i = 0; i < this.postProcessing.length; ++i) {
            if (this.postProcessing[i] != null) {
                ShortListIterator shortlistiterator = this.postProcessing[i].iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();
                    BlockPos blockposition = ProtoChunk.unpackOffsetCoordinates(oshort, this.getSectionYFromSectionIndex(i), chunkcoordintpair);
                    BlockState iblockdata = this.getBlockState(blockposition);
                    FluidState fluid = iblockdata.getFluidState();

                    if (!fluid.isEmpty()) {
                        fluid.tick(this.level, blockposition);
                    }

                    if (!(iblockdata.getBlock() instanceof LiquidBlock)) {
                        BlockState iblockdata1 = Block.updateFromNeighbourShapes(iblockdata, this.level, blockposition);

                        this.level.setBlock(blockposition, iblockdata1, 20);
                    }
                }

                this.postProcessing[i].clear();
            }
        }

        UnmodifiableIterator unmodifiableiterator = ImmutableList.copyOf(this.pendingBlockEntities.keySet()).iterator();

        while (unmodifiableiterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) unmodifiableiterator.next();

            this.getBlockEntity(blockposition1);
        }

        this.pendingBlockEntities.clear();
        this.upgradeData.upgrade(this);
    }

    @Nullable
    private BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag nbt) {
        BlockState iblockdata = this.getBlockState(pos);
        BlockEntity tileentity;

        if ("DUMMY".equals(nbt.getString("id"))) {
            if (iblockdata.hasBlockEntity()) {
                tileentity = ((EntityBlock) iblockdata.getBlock()).newBlockEntity(pos, iblockdata);
            } else {
                tileentity = null;
                LevelChunk.LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", pos, iblockdata);
            }
        } else {
            tileentity = BlockEntity.loadStatic(pos, iblockdata, nbt, this.level.registryAccess());
        }

        if (tileentity != null) {
            tileentity.setLevel(this.level);
            this.addAndRegisterBlockEntity(tileentity);
        } else {
            LevelChunk.LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", iblockdata, pos);
        }

        return tileentity;
    }

    public void unpackTicks(long time) {
        this.blockTicks.unpack(time);
        this.fluidTicks.unpack(time);
    }

    public void registerTickContainerInLevel(ServerLevel world) {
        world.getBlockTicks().addContainer(this.chunkPos, this.blockTicks);
        world.getFluidTicks().addContainer(this.chunkPos, this.fluidTicks);
    }

    public void unregisterTickContainerFromLevel(ServerLevel world) {
        world.getBlockTicks().removeContainer(this.chunkPos);
        world.getFluidTicks().removeContainer(this.chunkPos);
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ChunkStatus.FULL;
    }

    public FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? FullChunkStatus.FULL : (FullChunkStatus) this.fullStatus.get();
    }

    public void setFullStatus(Supplier<FullChunkStatus> levelTypeProvider) {
        this.fullStatus = levelTypeProvider;
    }

    public void clearAllBlockEntities() {
        this.blockEntities.values().forEach(BlockEntity::setRemoved);
        this.blockEntities.clear();
        this.tickersInLevel.values().forEach((chunk_d) -> {
            chunk_d.rebind(LevelChunk.NULL_TICKER);
        });
        this.tickersInLevel.clear();
    }

    public void registerAllBlockEntitiesAfterLevelLoad() {
        this.blockEntities.values().forEach((tileentity) -> {
            Level world = this.level;

            if (world instanceof ServerLevel worldserver) {
                this.addGameEventListener(tileentity, worldserver);
            }

            this.updateBlockEntityTicker(tileentity);
        });
    }

    private <T extends BlockEntity> void addGameEventListener(T blockEntity, ServerLevel world) {
        Block block = blockEntity.getBlockState().getBlock();

        if (block instanceof EntityBlock) {
            GameEventListener gameeventlistener = ((EntityBlock) block).getListener(world, blockEntity);

            if (gameeventlistener != null) {
                this.getListenerRegistry(SectionPos.blockToSectionCoord(blockEntity.getBlockPos().getY())).register(gameeventlistener);
            }
        }

    }

    private <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity) {
        BlockState iblockdata = blockEntity.getBlockState();
        BlockEntityTicker<T> blockentityticker = iblockdata.getTicker(this.level, (BlockEntityType<T>) blockEntity.getType()); // CraftBukkit - decompile error

        if (blockentityticker == null) {
            this.removeBlockEntityTicker(blockEntity.getBlockPos());
        } else {
            this.tickersInLevel.compute(blockEntity.getBlockPos(), (blockposition, chunk_d) -> {
                TickingBlockEntity tickingblockentity = this.createTicker(blockEntity, blockentityticker);

                if (chunk_d != null) {
                    chunk_d.rebind(tickingblockentity);
                    return chunk_d;
                } else if (this.isInLevel()) {
                    LevelChunk.RebindableTickingBlockEntityWrapper chunk_d1 = new LevelChunk.RebindableTickingBlockEntityWrapper(this, tickingblockentity);

                    this.level.addBlockEntityTicker(chunk_d1);
                    return chunk_d1;
                } else {
                    return null;
                }
            });
        }

    }

    private <T extends BlockEntity> TickingBlockEntity createTicker(T blockEntity, BlockEntityTicker<T> blockEntityTicker) {
        return new LevelChunk.BoundTickingBlockEntity<>(blockEntity, blockEntityTicker);
    }

    @FunctionalInterface
    public interface PostLoadProcessor {

        void run(LevelChunk chunk);
    }

    public static enum EntityCreationType {

        IMMEDIATE, QUEUED, CHECK;

        private EntityCreationType() {}
    }

    private class RebindableTickingBlockEntityWrapper implements TickingBlockEntity {

        private TickingBlockEntity ticker;

        RebindableTickingBlockEntityWrapper(final LevelChunk wrapped, final TickingBlockEntity tickingblockentity) {
            this.ticker = tickingblockentity;
        }

        void rebind(TickingBlockEntity wrapped) {
            this.ticker = wrapped;
        }

        @Override
        public void tick() {
            this.ticker.tick();
        }

        @Override
        public boolean isRemoved() {
            return this.ticker.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return this.ticker.getPos();
        }

        @Override
        public String getType() {
            return this.ticker.getType();
        }

        public String toString() {
            return String.valueOf(this.ticker) + " <wrapped>";
        }
    }

    private class BoundTickingBlockEntity<T extends BlockEntity> implements TickingBlockEntity {

        private final T blockEntity;
        private final BlockEntityTicker<T> ticker;
        private boolean loggedInvalidBlockState;

        BoundTickingBlockEntity(final BlockEntity tileentity, final BlockEntityTicker blockentityticker) {
            this.blockEntity = (T) tileentity; // CraftBukkit - decompile error
            this.ticker = blockentityticker;
        }

        @Override
        public void tick() {
            if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
                BlockPos blockposition = this.blockEntity.getBlockPos();

                if (LevelChunk.this.isTicking(blockposition)) {
                    try {
                        ProfilerFiller gameprofilerfiller = LevelChunk.this.level.getProfiler();

                        gameprofilerfiller.push(this::getType);
                        this.blockEntity.tickTimer.startTiming(); // Spigot
                        BlockState iblockdata = LevelChunk.this.getBlockState(blockposition);

                        if (this.blockEntity.getType().isValid(iblockdata)) {
                            this.ticker.tick(LevelChunk.this.level, this.blockEntity.getBlockPos(), iblockdata, this.blockEntity);
                            this.loggedInvalidBlockState = false;
                        } else if (!this.loggedInvalidBlockState) {
                            this.loggedInvalidBlockState = true;
                            LevelChunk.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", new Object[]{LogUtils.defer(this::getType), LogUtils.defer(this::getPos), iblockdata});
                        }

                        gameprofilerfiller.pop();
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking block entity");
                        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block entity being ticked");

                        this.blockEntity.fillCrashReportCategory(crashreportsystemdetails);
                        throw new ReportedException(crashreport);
                        // Spigot start
                    } finally {
                        this.blockEntity.tickTimer.stopTiming();
                        // Spigot end
                    }
                }
            }

        }

        @Override
        public boolean isRemoved() {
            return this.blockEntity.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return this.blockEntity.getBlockPos();
        }

        @Override
        public String getType() {
            return BlockEntityType.getKey(this.blockEntity.getType()).toString();
        }

        public String toString() {
            String s = this.getType();

            return "Level ticker for " + s + "@" + String.valueOf(this.getPos());
        }
    }
}
