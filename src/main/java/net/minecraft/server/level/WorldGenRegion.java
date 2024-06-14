package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.slf4j.Logger;

public class WorldGenRegion implements WorldGenLevel {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private final ChunkAccess center;
    private final ServerLevel level;
    private final long seed;
    private final LevelData levelData;
    private final RandomSource random;
    private final DimensionType dimensionType;
    private final WorldGenTickAccess<Block> blockTicks = new WorldGenTickAccess<>((blockposition) -> {
        return this.getChunk(blockposition).getBlockTicks();
    });
    private final WorldGenTickAccess<Fluid> fluidTicks = new WorldGenTickAccess<>((blockposition) -> {
        return this.getChunk(blockposition).getFluidTicks();
    });
    private final BiomeManager biomeManager;
    private final ChunkStep generatingStep;
    @Nullable
    private Supplier<String> currentlyGenerating;
    private final AtomicLong subTickCount = new AtomicLong();
    private static final ResourceLocation WORLDGEN_REGION_RANDOM = ResourceLocation.withDefaultNamespace("worldgen_region_random");

    // Paper start - rewrite chunk system
    /**
     * During feature generation, light data is not initialised and will always return 15 in Starlight. Vanilla
     * can possibly return 0 if partially initialised, which allows some mushroom blocks to generate.
     * In general, the brightness value from the light engine should not be used until the chunk is ready. To emulate
     * Vanilla behavior better, we return 0 as the brightness during world gen unless the target chunk is finished
     * lighting.
     */
    @Override
    public int getBrightness(final net.minecraft.world.level.LightLayer lightLayer, final BlockPos blockPos) {
        final ChunkAccess chunk = this.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getLayerListener(lightLayer).getLightValue(blockPos);
    }

    /**
     * See above
     */
    @Override
    public int getRawBrightness(final BlockPos blockPos, final int subtract) {
        final ChunkAccess chunk = this.getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        if (!chunk.isLightCorrect()) {
            return 0;
        }
        return this.getLightEngine().getRawBrightness(blockPos, subtract);
    }
    // Paper end - rewrite chunk system

    public WorldGenRegion(ServerLevel world, StaticCache2D<GenerationChunkHolder> chunks, ChunkStep generationStep, ChunkAccess centerPos) {
        this.generatingStep = generationStep;
        this.cache = chunks;
        this.center = centerPos;
        this.level = world;
        this.seed = world.getSeed();
        this.levelData = world.getLevelData();
        this.random = world.getChunkSource().randomState().getOrCreateRandomFactory(WorldGenRegion.WORLDGEN_REGION_RANDOM).at(this.center.getPos().getWorldPosition());
        this.dimensionType = world.dimensionType();
        this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed));
    }

    public boolean isOldChunkAround(ChunkPos chunkPos, int checkRadius) {
        return this.level.getChunkSource().chunkMap.isOldChunkAround(chunkPos, checkRadius);
    }

    public ChunkPos getCenter() {
        return this.center.getPos();
    }

    @Override
    public void setCurrentlyGenerating(@Nullable Supplier<String> structureName) {
        this.currentlyGenerating = structureName;
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY);
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        int k = this.center.getPos().getChessboardDistance(chunkX, chunkZ);
        ChunkStatus chunkstatus1 = k >= this.generatingStep.directDependencies().size() ? null : this.generatingStep.directDependencies().get(k);
        GenerationChunkHolder generationchunkholder;

        if (chunkstatus1 != null) {
            generationchunkholder = (GenerationChunkHolder) this.cache.get(chunkX, chunkZ);
            if (leastStatus.isOrBefore(chunkstatus1)) {
                ChunkAccess ichunkaccess = generationchunkholder.getChunkIfPresentUnchecked(chunkstatus1);

                if (ichunkaccess != null) {
                    return ichunkaccess;
                }
            }
        } else {
            generationchunkholder = null;
        }

        CrashReport crashreport = CrashReport.forThrowable(new IllegalStateException("Requested chunk unavailable during world generation"), "Exception generating new chunk");
        CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Chunk request details");

        crashreportsystemdetails.setDetail("Requested chunk", (Object) String.format(Locale.ROOT, "%d, %d", chunkX, chunkZ));
        crashreportsystemdetails.setDetail("Generating status", () -> {
            return this.generatingStep.targetStatus().getName();
        });
        Objects.requireNonNull(leastStatus);
        crashreportsystemdetails.setDetail("Requested status", leastStatus::getName);
        crashreportsystemdetails.setDetail("Actual status", () -> {
            return generationchunkholder == null ? "[out of cache bounds]" : generationchunkholder.getPersistedStatus().getName();
        });
        crashreportsystemdetails.setDetail("Maximum allowed status", () -> {
            return chunkstatus1 == null ? "null" : chunkstatus1.getName();
        });
        ChunkDependencies chunkdependencies = this.generatingStep.directDependencies();

        Objects.requireNonNull(chunkdependencies);
        crashreportsystemdetails.setDetail("Dependencies", chunkdependencies::toString);
        crashreportsystemdetails.setDetail("Requested distance", (Object) k);
        ChunkPos chunkcoordintpair = this.center.getPos();

        Objects.requireNonNull(chunkcoordintpair);
        crashreportsystemdetails.setDetail("Generating chunk", chunkcoordintpair::toString);
        throw new ReportedException(crashreport);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        int k = this.center.getPos().getChessboardDistance(chunkX, chunkZ);

        return k < this.generatingStep.directDependencies().size();
    }

    // Paper start - if loaded util
    @Nullable
    @Override
    public ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        return this.getChunk(x, z, ChunkStatus.FULL, false);
    }

    @Override
    public final BlockState getBlockStateIfLoaded(BlockPos blockposition) {
        ChunkAccess chunk = this.getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(blockposition);
    }

    @Override
    public final FluidState getFluidIfLoaded(BlockPos blockposition) {
        ChunkAccess chunk = this.getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);
        return chunk == null ? null : chunk.getFluidState(blockposition);
    }
    // Paper end

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getChunk(pos).getFluidState(pos);
    }

    @Nullable
    @Override
    public Player getNearestPlayer(double x, double y, double z, double maxDistance, Predicate<Entity> targetPredicate) {
        return null;
    }

    @Override
    public int getSkyDarken() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        return this.level.getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth) {
        BlockState iblockdata = this.getBlockState(pos);

        if (iblockdata.isAir()) {
            return false;
        } else {
            if (drop) LOGGER.warn("Potential async entity add during worldgen", new Throwable()); // Paper - Fix async entity add due to fungus trees; log when this happens
            if (false) { // CraftBukkit - SPIGOT-6833: Do not drop during world generation
                BlockEntity tileentity = iblockdata.hasBlockEntity() ? this.getBlockEntity(pos) : null;

                Block.dropResources(iblockdata, this.level, pos, tileentity, breakingEntity, ItemStack.EMPTY);
            }

            return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3, maxUpdateDepth);
        }
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        ChunkAccess ichunkaccess = this.getChunk(pos);
        BlockEntity tileentity = ichunkaccess.getBlockEntity(pos);

        if (tileentity != null) {
            return tileentity;
        } else {
            CompoundTag nbttagcompound = ichunkaccess.getBlockEntityNbt(pos);
            BlockState iblockdata = ichunkaccess.getBlockState(pos);

            if (nbttagcompound != null) {
                if ("DUMMY".equals(nbttagcompound.getString("id"))) {
                    if (!iblockdata.hasBlockEntity()) {
                        return null;
                    }

                    tileentity = ((EntityBlock) iblockdata.getBlock()).newBlockEntity(pos, iblockdata);
                } else {
                    tileentity = BlockEntity.loadStatic(pos, iblockdata, nbttagcompound, this.level.registryAccess());
                }

                if (tileentity != null) {
                    ichunkaccess.setBlockEntity(tileentity);
                    return tileentity;
                }
            }

            if (iblockdata.hasBlockEntity()) {
                WorldGenRegion.LOGGER.warn("Tried to access a block entity before it was created. {}", pos);
            }

            return null;
        }
    }

    private boolean hasSetFarWarned = false; // Paper - Buffer OOB setBlock calls
    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkPos chunkcoordintpair = this.getCenter();
        int k = Math.abs(chunkcoordintpair.x - i);
        int l = Math.abs(chunkcoordintpair.z - j);

        if (k <= this.generatingStep.blockStateWriteRadius() && l <= this.generatingStep.blockStateWriteRadius()) {
            if (this.center.isUpgrading()) {
                LevelHeightAccessor levelheightaccessor = this.center.getHeightAccessorForGeneration();

                if (pos.getY() < levelheightaccessor.getMinBuildHeight() || pos.getY() >= levelheightaccessor.getMaxBuildHeight()) {
                    return false;
                }
            }

            return true;
        } else {
            // Paper start - Buffer OOB setBlock calls
            if (!hasSetFarWarned) {
            Util.logAndPauseIfInIde("Detected setBlock in a far chunk [" + i + ", " + j + "], pos: " + String.valueOf(pos) + ", status: " + String.valueOf(this.generatingStep.targetStatus()) + (this.currentlyGenerating == null ? "" : ", currently generating: " + (String) this.currentlyGenerating.get()));
                hasSetFarWarned = true;
                if (this.getServer() != null && this.getServer().isDebugging()) {
                    io.papermc.paper.util.TraceUtil.dumpTraceForThread("far setBlock call");
                }
            }
            // Paper end - Buffer OOB setBlock calls
            return false;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        if (!this.ensureCanWrite(pos)) {
            return false;
        } else {
            ChunkAccess ichunkaccess = this.getChunk(pos);
            BlockState iblockdata1 = ichunkaccess.setBlockState(pos, state, false); final BlockState previousBlockState = iblockdata1; // Paper - Clear block entity before setting up a DUMMY block entity - obfhelper

            if (iblockdata1 != null) {
                this.level.onBlockStateChange(pos, iblockdata1, state);
            }

            if (state.hasBlockEntity()) {
                if (ichunkaccess.getPersistedStatus().getChunkType() == ChunkType.LEVELCHUNK) {
                    BlockEntity tileentity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);

                    if (tileentity != null) {
                        ichunkaccess.setBlockEntity(tileentity);
                    } else {
                        ichunkaccess.removeBlockEntity(pos);
                    }
                } else {
                    // Paper start - Clear block entity before setting up a DUMMY block entity
                    // The concept of removing a block entity when the block itself changes is generally lifted
                    // from LevelChunk#setBlockState.
                    // It is however to note that this may only run if the block actually changes.
                    // Otherwise a chest block entity generated by a structure template that is later "updated" to
                    // be waterlogged would remove its existing block entity (see PaperMC/Paper#10750)
                    // This logic is *also* found in LevelChunk#setBlockState.
                    if (previousBlockState != null && !java.util.Objects.equals(previousBlockState.getBlock(), state.getBlock())) {
                        ichunkaccess.removeBlockEntity(pos);
                    }
                    // Paper end - Clear block entity before setting up a DUMMY block entity
                    CompoundTag nbttagcompound = new CompoundTag();

                    nbttagcompound.putInt("x", pos.getX());
                    nbttagcompound.putInt("y", pos.getY());
                    nbttagcompound.putInt("z", pos.getZ());
                    nbttagcompound.putString("id", "DUMMY");
                    ichunkaccess.setBlockEntityNbt(nbttagcompound);
                }
            } else if (iblockdata1 != null && iblockdata1.hasBlockEntity()) {
                ichunkaccess.removeBlockEntity(pos);
            }

            if (state.hasPostProcess(this, pos)) {
                this.markPosForPostprocessing(pos);
            }

            return true;
        }
    }

    private void markPosForPostprocessing(BlockPos pos) {
        this.getChunk(pos).markPosForPostprocessing(pos);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        int i = SectionPos.blockToSectionCoord(entity.getBlockX());
        int j = SectionPos.blockToSectionCoord(entity.getBlockZ());

        this.getChunk(i, j).addEntity(entity);
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean move) {
        return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** @deprecated */
    @Deprecated
    @Override
    public ServerLevel getLevel() {
        return this.level;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        if (!this.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            throw new RuntimeException("We are asking a region for a chunk out of bound");
        } else {
            return new DifficultyInstance(this.level.getDifficulty(), this.level.getDayTime(), 0L, this.level.getMoonBrightness());
        }
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return this.level.getServer();
    }

    @Override
    public ChunkSource getChunkSource() {
        return this.level.getChunkSource();
    }

    @Override
    public long getSeed() {
        return this.seed;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public int getSeaLevel() {
        return this.level.getSeaLevel();
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        return this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(heightmap, x & 15, z & 15) + 1;
    }

    @Override
    public void playSound(@Nullable Player source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {}

    @Override
    public void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    @Override
    public void levelEvent(@Nullable Player player, int eventId, BlockPos pos, int data) {}

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 emitterPos, GameEvent.Context emitter) {}

    @Override
    public DimensionType dimensionType() {
        return this.dimensionType;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) {
        return state.test(this.getFluidState(pos));
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB box, @Nullable Predicate<? super Entity> predicate) {
        return Collections.emptyList();
    }

    @Override
    public List<Player> players() {
        return Collections.emptyList();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public long nextSubTickCount() {
        return this.subTickCount.getAndIncrement();
    }
}
