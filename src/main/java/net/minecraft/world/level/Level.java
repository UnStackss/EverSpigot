package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import io.papermc.paper.util.MCUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Scoreboard;

// CraftBukkit start
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.block.BlockPhysicsEvent;
// CraftBukkit end

public abstract class Level implements LevelAccessor, AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel, ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter, ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel { // Paper - rewrite chunk system // Paper - optimise collisions

    public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));
    public static final int MAX_LEVEL_SIZE = 30000000;
    public static final int LONG_PARTICLE_CLIP_RANGE = 512;
    public static final int SHORT_PARTICLE_CLIP_RANGE = 32;
    public static final int MAX_BRIGHTNESS = 15;
    public static final int TICKS_PER_DAY = 24000;
    public static final int MAX_ENTITY_SPAWN_Y = 20000000;
    public static final int MIN_ENTITY_SPAWN_Y = -20000000;
    public final List<TickingBlockEntity> blockEntityTickers = Lists.newArrayList(); // Paper - public
    protected final NeighborUpdater neighborUpdater;
    private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
    private boolean tickingBlockEntities;
    public final Thread thread;
    private final boolean isDebug;
    private int skyDarken;
    protected int randValue = RandomSource.create().nextInt();
    protected final int addend = 1013904223;
    protected float oRainLevel;
    public float rainLevel;
    protected float oThunderLevel;
    public float thunderLevel;
    public final RandomSource random = RandomSource.create();
    /** @deprecated */
    @Deprecated
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
    private final Holder<DimensionType> dimensionTypeRegistration;
    public final WritableLevelData levelData;
    private final Supplier<ProfilerFiller> profiler;
    public final boolean isClientSide;
    private final WorldBorder worldBorder;
    private final BiomeManager biomeManager;
    private final ResourceKey<Level> dimension;
    private final RegistryAccess registryAccess;
    private final DamageSources damageSources;
    private long subTickCount;

    // CraftBukkit start Added the following
    private final CraftWorld world;
    public boolean pvpMode;
    public org.bukkit.generator.ChunkGenerator generator;

    public boolean preventPoiUpdated = false; // CraftBukkit - SPIGOT-5710
    public boolean captureBlockStates = false;
    public boolean captureTreeGeneration = false;
    public boolean isBlockPlaceCancelled = false; // Paper - prevent calling cleanup logic when undoing a block place upon a cancelled BlockPlaceEvent
    public Map<BlockPos, org.bukkit.craftbukkit.block.CraftBlockState> capturedBlockStates = new java.util.LinkedHashMap<>(); // Paper
    public Map<BlockPos, BlockEntity> capturedTileEntities = new java.util.LinkedHashMap<>(); // Paper - Retain block place order when capturing blockstates
    public List<ItemEntity> captureDrops;
    public final it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<SpawnCategory> ticksPerSpawnCategory = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>();
    // Paper start
    public int wakeupInactiveRemainingAnimals;
    public int wakeupInactiveRemainingFlying;
    public int wakeupInactiveRemainingMonsters;
    public int wakeupInactiveRemainingVillagers;
    // Paper end
    public boolean populating;
    public final org.spigotmc.SpigotWorldConfig spigotConfig; // Spigot
    // Paper start - add paper world config
    private final io.papermc.paper.configuration.WorldConfiguration paperConfig;
    public io.papermc.paper.configuration.WorldConfiguration paperConfig() {
        return this.paperConfig;
    }
    // Paper end - add paper world config

    public final com.destroystokyo.paper.antixray.ChunkPacketBlockController chunkPacketBlockController; // Paper - Anti-Xray
    public final co.aikar.timings.WorldTimingsHandler timings; // Paper
    public static BlockPos lastPhysicsProblem; // Spigot
    private org.spigotmc.TickLimiter entityLimiter;
    private org.spigotmc.TickLimiter tileLimiter;
    private int tileTickPosition;
    public final Map<Explosion.CacheKey, Float> explosionDensityCache = new HashMap<>(); // Paper - Optimize explosions
    public java.util.ArrayDeque<net.minecraft.world.level.block.RedstoneTorchBlock.Toggle> redstoneUpdateInfos; // Paper - Faster redstone torch rapid clock removal; Move from Map in BlockRedstoneTorch to here

    public CraftWorld getWorld() {
        return this.world;
    }

    public CraftServer getCraftServer() {
        return (CraftServer) Bukkit.getServer();
    }

    // Paper start - Use getChunkIfLoadedImmediately
    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkIfLoaded(chunkX, chunkZ) != null;
    }
    // Paper end - Use getChunkIfLoadedImmediately
    // Paper start - per world ticks per spawn
    private int getTicksPerSpawn(SpawnCategory spawnCategory) {
        final int perWorld = this.paperConfig().entities.spawning.ticksPerSpawn.getInt(CraftSpawnCategory.toNMS(spawnCategory));
        if (perWorld >= 0) {
            return perWorld;
        }
        return this.getCraftServer().getTicksPerSpawns(spawnCategory);
    }
    // Paper end

    public abstract ResourceKey<LevelStem> getTypeKey();

    // Paper start - rewrite chunk system
    private ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup entityLookup;

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup moonrise$getEntityLookup() {
        return this.entityLookup;
    }

    @Override
    public void moonrise$setEntityLookup(final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup entityLookup) {
        if (this.entityLookup != null && !(this.entityLookup instanceof ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl.DefaultEntityLookup)) {
            throw new IllegalStateException("Entity lookup already initialised");
        }
        this.entityLookup = entityLookup;
    }

    @Override
    public final <T extends Entity> List<T> getEntitiesOfClass(final Class<T> entityClass, final AABB boundingBox, final Predicate<? super T> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<T> ret = new java.util.ArrayList<>();

        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(entityClass, null, boundingBox, ret, predicate);

        return ret;
    }

    @Override
    public final List<Entity> moonrise$getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final List<Entity> ret = new java.util.ArrayList<>();

        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getHardCollidingEntities(entity, box, ret, predicate);

        return ret;
    }

    @Override
    public LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, false);
    }

    @Override
    public ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    @Override
    public ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final ChunkStatus leastStatus) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, leastStatus, false);
    }

    @Override
    public void moonrise$midTickTasks() {
        // no-op on ClientLevel
    }
    /**
     * @reason Turn all getChunk(x, z, status) calls into virtual invokes, instead of interface invokes:
     *         1. The interface invoke is expensive
     *         2. The method makes other interface invokes (again, expensive)
     *         Instead, we just directly call getChunk(x, z, status, true) which avoids the interface invokes entirely.
     * @author Spottedleaf
     */
    @Override
    public ChunkAccess getChunk(final int x, final int z, final ChunkStatus status) {
        return ((Level)(Object)this).getChunk(x, z, status, true);
    }

    @Override
    public BlockPos getHeightmapPos(Heightmap.Types types, BlockPos blockPos) {
        return new BlockPos(blockPos.getX(), this.getHeight(types, blockPos.getX(), blockPos.getZ()), blockPos.getZ());
    }
    // Paper end - rewrite chunk system
    // Paper start - optimise collisions
    private final int minSection;
    private final int maxSection;

    @Override
    public final int moonrise$getMinSection() {
        return this.minSection;
    }

    @Override
    public final int moonrise$getMaxSection() {
        return this.maxSection;
    }

    /**
     * Route to faster lookup.
     * See {@link EntityGetter#isUnobstructed(Entity, VoxelShape)} for expected behavior
     * @author Spottedleaf
     */
    @Override
    public final boolean isUnobstructed(final Entity entity) {
        final AABB boundingBox = entity.getBoundingBox();
        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isEmpty(boundingBox)) {
            return false;
        }

        final List<Entity> entities = this.getEntities(
            entity,
            boundingBox.inflate(-ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON, -ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON, -ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON),
            null
        );

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator() || otherEntity.isRemoved() || !otherEntity.blocksBuilding || otherEntity.isPassengerOfSameVehicle(entity)) {
                continue;
            }

            return false;
        }

        return true;
    }


    private static net.minecraft.world.phys.BlockHitResult miss(final ClipContext clipContext) {
        final Vec3 to = clipContext.getTo();
        final Vec3 from = clipContext.getFrom();

        return net.minecraft.world.phys.BlockHitResult.miss(to, Direction.getNearest(from.x - to.x, from.y - to.y, from.z - to.z), BlockPos.containing(to.x, to.y, to.z));
    }

    private static final FluidState AIR_FLUIDSTATE = Fluids.EMPTY.defaultFluidState();

    private static net.minecraft.world.phys.BlockHitResult fastClip(final Vec3 from, final Vec3 to, final Level level,
                                                                    final ClipContext clipContext) {
        final double adjX = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON * (from.x - to.x);
        final double adjY = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON * (from.y - to.y);
        final double adjZ = ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON * (from.z - to.z);

        if (adjX == 0.0 && adjY == 0.0 && adjZ == 0.0) {
            return miss(clipContext);
        }

        final double toXAdj = to.x - adjX;
        final double toYAdj = to.y - adjY;
        final double toZAdj = to.z - adjZ;
        final double fromXAdj = from.x + adjX;
        final double fromYAdj = from.y + adjY;
        final double fromZAdj = from.z + adjZ;

        int currX = Mth.floor(fromXAdj);
        int currY = Mth.floor(fromYAdj);
        int currZ = Mth.floor(fromZAdj);

        final BlockPos.MutableBlockPos currPos = new BlockPos.MutableBlockPos();

        final double diffX = toXAdj - fromXAdj;
        final double diffY = toYAdj - fromYAdj;
        final double diffZ = toZAdj - fromZAdj;

        final double dxDouble = Math.signum(diffX);
        final double dyDouble = Math.signum(diffY);
        final double dzDouble = Math.signum(diffZ);

        final int dx = (int)dxDouble;
        final int dy = (int)dyDouble;
        final int dz = (int)dzDouble;

        final double normalizedDiffX = diffX == 0.0 ? Double.MAX_VALUE : dxDouble / diffX;
        final double normalizedDiffY = diffY == 0.0 ? Double.MAX_VALUE : dyDouble / diffY;
        final double normalizedDiffZ = diffZ == 0.0 ? Double.MAX_VALUE : dzDouble / diffZ;

        double normalizedCurrX = normalizedDiffX * (diffX > 0.0 ? (1.0 - Mth.frac(fromXAdj)) : Mth.frac(fromXAdj));
        double normalizedCurrY = normalizedDiffY * (diffY > 0.0 ? (1.0 - Mth.frac(fromYAdj)) : Mth.frac(fromYAdj));
        double normalizedCurrZ = normalizedDiffZ * (diffZ > 0.0 ? (1.0 - Mth.frac(fromZAdj)) : Mth.frac(fromZAdj));

        net.minecraft.world.level.chunk.LevelChunkSection[] lastChunk = null;
        net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.world.level.block.state.BlockState> lastSection = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkY = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final int minSection = ((ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel)level).moonrise$getMinSection();

        for (;;) {
            currPos.set(currX, currY, currZ);

            final int newChunkX = currX >> 4;
            final int newChunkY = currY >> 4;
            final int newChunkZ = currZ >> 4;

            final int chunkDiff = ((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ));
            final int chunkYDiff = newChunkY ^ lastChunkY;

            if ((chunkDiff | chunkYDiff) != 0) {
                if (chunkDiff != 0) {
                    lastChunk = level.getChunk(newChunkX, newChunkZ).getSections();
                }
                final int sectionY = newChunkY - minSection;
                lastSection = sectionY >= 0 && sectionY < lastChunk.length ? lastChunk[sectionY].states : null;

                lastChunkX = newChunkX;
                lastChunkY = newChunkY;
                lastChunkZ = newChunkZ;
            }

            final BlockState blockState;
            if (lastSection != null && !(blockState = lastSection.get((currX & 15) | ((currZ & 15) << 4) | ((currY & 15) << (4+4)))).isAir()) {
                final VoxelShape blockCollision = clipContext.getBlockShape(blockState, level, currPos);

                final net.minecraft.world.phys.BlockHitResult blockHit = blockCollision.isEmpty() ? null : level.clipWithInteractionOverride(from, to, currPos, blockCollision, blockState);

                final VoxelShape fluidCollision;
                final FluidState fluidState;
                if (clipContext.fluid != ClipContext.Fluid.NONE && (fluidState = blockState.getFluidState()) != AIR_FLUIDSTATE) {
                    fluidCollision = clipContext.getFluidShape(fluidState, level, currPos);

                    final net.minecraft.world.phys.BlockHitResult fluidHit = fluidCollision.clip(from, to, currPos);

                    if (fluidHit != null) {
                        if (blockHit == null) {
                            return fluidHit;
                        }

                        return from.distanceToSqr(blockHit.getLocation()) <= from.distanceToSqr(fluidHit.getLocation()) ? blockHit : fluidHit;
                    }
                }

                if (blockHit != null) {
                    return blockHit;
                }
            } // else: usually fall here

            if (normalizedCurrX > 1.0 && normalizedCurrY > 1.0 && normalizedCurrZ > 1.0) {
                return miss(clipContext);
            }

            // inc the smallest normalized coordinate

            if (normalizedCurrX < normalizedCurrY) {
                if (normalizedCurrX < normalizedCurrZ) {
                    currX += dx;
                    normalizedCurrX += normalizedDiffX;
                } else {
                    // x < y && x >= z <--> z < y && z <= x
                    currZ += dz;
                    normalizedCurrZ += normalizedDiffZ;
                }
            } else if (normalizedCurrY < normalizedCurrZ) {
                // y <= x && y < z
                currY += dy;
                normalizedCurrY += normalizedDiffY;
            } else {
                // y <= x && z <= y <--> z <= y && z <= x
                currZ += dz;
                normalizedCurrZ += normalizedDiffZ;
            }
        }
    }

    /**
     * @reason Route to optimized call
     * @author Spottedleaf
     */
    @Override
    public final net.minecraft.world.phys.BlockHitResult clip(final ClipContext clipContext) {
        // can only do this in this class, as not everything that implements BlockGetter can retrieve chunks
        return fastClip(clipContext.getFrom(), clipContext.getTo(), (Level)(Object)this, clipContext);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Override
    public final boolean collidesWithSuffocatingBlock(final Entity entity, final AABB box) {
        return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getCollisionsForBlocksOrWorldBorder((Level)(Object)this, entity, box, null, null,
            ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_ONLY,
            (final BlockState state, final BlockPos pos) -> {
                return state.isSuffocating((Level)(Object)Level.this, pos);
            }
        );
    }

    private static VoxelShape inflateAABBToVoxel(final AABB aabb, final double x, final double y, final double z) {
        return net.minecraft.world.phys.shapes.Shapes.create(
            aabb.minX - x,
            aabb.minY - y,
            aabb.minZ - z,

            aabb.maxX + x,
            aabb.maxY + y,
            aabb.maxZ + z
        );
    }

    /**
     * @reason Use optimised OR operator join strategy, avoid streams
     * @author Spottedleaf
     */
    @Override
    public final java.util.Optional<net.minecraft.world.phys.Vec3> findFreePosition(final Entity entity, final VoxelShape boundsShape, final Vec3 fromPosition,
                                                                                    final double rangeX, final double rangeY, final double rangeZ) {
        if (boundsShape.isEmpty()) {
            return java.util.Optional.empty();
        }

        final double expandByX = rangeX * 0.5;
        final double expandByY = rangeY * 0.5;
        final double expandByZ = rangeZ * 0.5;

        // note: it is useless to look at shapes outside of range / 2.0
        final AABB collectionVolume = boundsShape.bounds().inflate(expandByX, expandByY, expandByZ);

        final List<AABB> aabbs = new java.util.ArrayList<>();
        final List<VoxelShape> voxels = new java.util.ArrayList<>();

        ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getCollisionsForBlocksOrWorldBorder(
            (Level)(Object)this, entity, collectionVolume, voxels, aabbs,
            ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
            null
        );

        final WorldBorder worldBorder = this.getWorldBorder();
        if (worldBorder != null) {
            aabbs.removeIf((final AABB aabb) -> {
                return !worldBorder.isWithinBounds(aabb);
            });
            voxels.removeIf((final VoxelShape shape) -> {
                return !worldBorder.isWithinBounds(shape.bounds());
            });
        }

        // push voxels into aabbs
        for (int i = 0, len = voxels.size(); i < len; ++i) {
            aabbs.addAll(voxels.get(i).toAabbs());
        }

        // expand AABBs
        final VoxelShape first = aabbs.isEmpty() ? net.minecraft.world.phys.shapes.Shapes.empty() : inflateAABBToVoxel(aabbs.get(0), expandByX, expandByY, expandByZ);
        final VoxelShape[] rest = new VoxelShape[Math.max(0, aabbs.size() - 1)];

        for (int i = 1, len = aabbs.size(); i < len; ++i) {
            rest[i - 1] = inflateAABBToVoxel(aabbs.get(i), expandByX, expandByY, expandByZ);
        }

        // use optimized implementation of ORing the shapes together
        final VoxelShape joined = net.minecraft.world.phys.shapes.Shapes.or(first, rest);

        // find free space
        // can use unoptimized join here (instead of join()), as closestPointTo uses toAabbs()
        final VoxelShape freeSpace = net.minecraft.world.phys.shapes.Shapes.joinUnoptimized(boundsShape, joined, net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);

        return freeSpace.closestPointTo(fromPosition);
    }

    /**
     * @reason Route to faster logic
     * @author Spottedleaf
     */
    @Override
    public final java.util.Optional<net.minecraft.core.BlockPos> findSupportingBlock(final Entity entity, final AABB aabb) {
        final int minBlockX = Mth.floor(aabb.minX - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockX = Mth.floor(aabb.maxX + ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockY = Mth.floor(aabb.minY - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockY = Mth.floor(aabb.maxY + ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) + 1;

        final int minBlockZ = Mth.floor(aabb.minZ - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) - 1;
        final int maxBlockZ = Mth.floor(aabb.maxZ + ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) + 1;

        ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.LazyEntityCollisionContext collisionContext = null;

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos selected = null;
        double selectedDistance = Double.MAX_VALUE;

        final Vec3 entityPos = entity.position();

        LevelChunk lastChunk = null;
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        final ChunkSource chunkSource = this.getChunkSource();

        for (int currZ = minBlockZ; currZ <= maxBlockZ; ++currZ) {
            pos.setZ(currZ);
            for (int currX = minBlockX; currX <= maxBlockX; ++currX) {
                pos.setX(currX);

                final int newChunkX = currX >> 4;
                final int newChunkZ = currZ >> 4;

                if (((newChunkX ^ lastChunkX) | (newChunkZ ^ lastChunkZ)) != 0) {
                    lastChunkX = newChunkX;
                    lastChunkZ = newChunkZ;
                    lastChunk = (LevelChunk)chunkSource.getChunk(newChunkX, newChunkZ, ChunkStatus.FULL, false);
                }

                if (lastChunk == null) {
                    continue;
                }
                for (int currY = minBlockY; currY <= maxBlockY; ++currY) {
                    int edgeCount = ((currX == minBlockX || currX == maxBlockX) ? 1 : 0) +
                        ((currY == minBlockY || currY == maxBlockY) ? 1 : 0) +
                        ((currZ == minBlockZ || currZ == maxBlockZ) ? 1 : 0);
                    if (edgeCount == 3) {
                        continue;
                    }

                    pos.setY(currY);

                    final double distance = pos.distToCenterSqr(entityPos);
                    if (distance > selectedDistance || (distance == selectedDistance && selected.compareTo(pos) >= 0)) {
                        continue;
                    }

                    final BlockState state = ((ca.spottedleaf.moonrise.patches.chunk_getblock.GetBlockChunk)lastChunk).moonrise$getBlock(currX, currY, currZ);
                    if (((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)state).moonrise$emptyCollisionShape()) {
                        continue;
                    }

                    VoxelShape blockCollision = ((ca.spottedleaf.moonrise.patches.collisions.block.CollisionBlockState)state).moonrise$getConstantCollisionShape();

                    if ((edgeCount != 1 || state.hasLargeCollisionShape()) && (edgeCount != 2 || state.getBlock() == Blocks.MOVING_PISTON)) {
                        if (collisionContext == null) {
                            collisionContext = new ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.LazyEntityCollisionContext(entity);
                        }

                        if (blockCollision == null) {
                            blockCollision = state.getCollisionShape((Level)(Object)this, pos, collisionContext);
                        }

                        if (blockCollision.isEmpty()) {
                            continue;
                        }

                        // avoid VoxelShape#move by shifting the entity collision shape instead
                        final AABB shiftedAABB = aabb.move(-(double)currX, -(double)currY, -(double)currZ);

                        final AABB singleAABB = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)blockCollision).moonrise$getSingleAABBRepresentation();
                        if (singleAABB != null) {
                            if (!ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.voxelShapeIntersect(singleAABB, shiftedAABB)) {
                                continue;
                            }

                            selected = pos.immutable();
                            selectedDistance = distance;
                            continue;
                        }

                        if (!ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.voxelShapeIntersectNoEmpty(blockCollision, shiftedAABB)) {
                            continue;
                        }

                        selected = pos.immutable();
                        selectedDistance = distance;
                        continue;
                    }
                }
            }
        }

        return java.util.Optional.ofNullable(selected);
    }
    // Paper end - optimise collisions
    // Paper start - optimise random ticking
    @Override
    public abstract Holder<Biome> getUncachedNoiseBiome(final int x, final int y, final int z);

    /**
     * @reason Make getChunk and getUncachedNoiseBiome virtual calls instead of interface calls
     *         by implementing the superclass method in this class.
     * @author Spottedleaf
     */
    @Override
    public Holder<Biome> getNoiseBiome(final int x, final int y, final int z) {
        final ChunkAccess chunk = this.getChunk(x >> 2, z >> 2, ChunkStatus.BIOMES, false);

        return chunk != null ? chunk.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
    }
    // Paper end - optimise random ticking

    protected Level(WritableLevelData worlddatamutable, ResourceKey<Level> resourcekey, RegistryAccess iregistrycustom, Holder<DimensionType> holder, Supplier<ProfilerFiller> supplier, boolean flag, boolean flag1, long i, int j, org.bukkit.generator.ChunkGenerator gen, org.bukkit.generator.BiomeProvider biomeProvider, org.bukkit.World.Environment env, java.util.function.Function<org.spigotmc.SpigotWorldConfig, io.papermc.paper.configuration.WorldConfiguration> paperWorldConfigCreator, java.util.concurrent.Executor executor) { // Paper - create paper world config & Anti-Xray
        this.spigotConfig = new org.spigotmc.SpigotWorldConfig(((net.minecraft.world.level.storage.PrimaryLevelData) worlddatamutable).getLevelName()); // Spigot
        this.paperConfig = paperWorldConfigCreator.apply(this.spigotConfig); // Paper - create paper world config
        this.generator = gen;
        this.world = new CraftWorld((ServerLevel) this, gen, biomeProvider, env);

        // CraftBukkit Ticks things
        for (SpawnCategory spawnCategory : SpawnCategory.values()) {
            if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                this.ticksPerSpawnCategory.put(spawnCategory, this.getTicksPerSpawn(spawnCategory)); // Paper
            }
        }

        // CraftBukkit end
        this.profiler = supplier;
        this.levelData = worlddatamutable;
        this.dimensionTypeRegistration = holder;
        final DimensionType dimensionmanager = (DimensionType) holder.value();

        this.dimension = resourcekey;
        this.isClientSide = flag;
        if (dimensionmanager.coordinateScale() != 1.0D) {
            this.worldBorder = new WorldBorder() { // CraftBukkit - decompile error
                @Override
                public double getCenterX() {
                    return super.getCenterX(); // CraftBukkit
                }

                @Override
                public double getCenterZ() {
                    return super.getCenterZ(); // CraftBukkit
                }
            };
        } else {
            this.worldBorder = new WorldBorder();
        }

        this.thread = Thread.currentThread();
        this.biomeManager = new BiomeManager(this, i);
        this.isDebug = flag1;
        this.neighborUpdater = new CollectingNeighborUpdater(this, j);
        this.registryAccess = iregistrycustom;
        this.damageSources = new DamageSources(iregistrycustom);
        // CraftBukkit start
        this.getWorldBorder().world = (ServerLevel) this;
        // From PlayerList.setPlayerFileData
        this.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder border, double size) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderSizePacket(border), border.world);
            }

            @Override
            public void onBorderSizeLerping(WorldBorder border, double fromSize, double toSize, long time) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderLerpSizePacket(border), border.world);
            }

            @Override
            public void onBorderCenterSet(WorldBorder border, double centerX, double centerZ) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderCenterPacket(border), border.world);
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), border.world);
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder border, int warningBlockDistance) {
                Level.this.getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), border.world);
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {}

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZoneRadius) {}
        });
        // CraftBukkit end
        this.timings = new co.aikar.timings.WorldTimingsHandler(this); // Paper - code below can generate new world and access timings
        this.entityLimiter = new org.spigotmc.TickLimiter(this.spigotConfig.entityMaxTickTime);
        this.tileLimiter = new org.spigotmc.TickLimiter(this.spigotConfig.tileMaxTickTime);
        this.entityLookup = new ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl.DefaultEntityLookup(this); // Paper - rewrite chunk system
        // Paper start - optimise collisions
        this.minSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMinSection(this);
        this.maxSection = ca.spottedleaf.moonrise.common.util.WorldUtil.getMaxSection(this);
        // Paper end - optimise collisions
        this.chunkPacketBlockController = this.paperConfig().anticheat.antiXray.enabled ? new com.destroystokyo.paper.antixray.ChunkPacketBlockControllerAntiXray(this, executor) : com.destroystokyo.paper.antixray.ChunkPacketBlockController.NO_OPERATION_INSTANCE; // Paper - Anti-Xray
    }

    // Paper start - Cancel hit for vanished players
    // ret true if no collision
    public final boolean checkEntityCollision(BlockState data, Entity source, net.minecraft.world.phys.shapes.CollisionContext voxelshapedcollision,
                                              BlockPos position, boolean checkCanSee) {
        // Copied from IWorldReader#a(IBlockData, BlockPosition, VoxelShapeCollision) & EntityAccess#a(Entity, VoxelShape)
        net.minecraft.world.phys.shapes.VoxelShape voxelshape = data.getCollisionShape(this, position, voxelshapedcollision);
        if (voxelshape.isEmpty()) {
            return true;
        }

        voxelshape = voxelshape.move((double) position.getX(), (double) position.getY(), (double) position.getZ());
        if (voxelshape.isEmpty()) {
            return true;
        }

        List<Entity> entities = this.getEntities(null, voxelshape.bounds());
        for (int i = 0, len = entities.size(); i < len; ++i) {
            Entity entity = entities.get(i);

            if (checkCanSee && source instanceof net.minecraft.server.level.ServerPlayer && entity instanceof net.minecraft.server.level.ServerPlayer
                && !((net.minecraft.server.level.ServerPlayer) source).getBukkitEntity().canSee(((net.minecraft.server.level.ServerPlayer) entity).getBukkitEntity())) {
                continue;
            }

            // !entity1.dead && entity1.i && (entity == null || !entity1.x(entity));
            // elide the last check since vanilla calls with entity = null
            // only we care about the source for the canSee check
            if (entity.isRemoved() || !entity.blocksBuilding) {
                continue;
            }

            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(voxelshape, net.minecraft.world.phys.shapes.Shapes.create(entity.getBoundingBox()), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                return false;
            }
        }

        return true;
    }
    // Paper end - Cancel hit for vanished players
    @Override
    public boolean isClientSide() {
        return this.isClientSide;
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return null;
    }

    // Paper start - Broken down method of raytracing for EntityLiving#hasLineOfSight, replaces BlockGetter#clip(CollisionContext)
    public net.minecraft.world.phys.BlockHitResult.Type clipDirect(Vec3 start, Vec3 end, net.minecraft.world.phys.shapes.CollisionContext context) {
        // most of this code comes from BlockGetter#clip(CollisionContext, BiFunction, Function), but removes the needless functions
        if (start.equals(end)) {
            return net.minecraft.world.phys.BlockHitResult.Type.MISS;
        }

        final double endX = Mth.lerp(-1.0E-7D, end.x, start.x);
        final double endY = Mth.lerp(-1.0E-7D, end.y, start.y);
        final double endZ = Mth.lerp(-1.0E-7D, end.z, start.z);

        final double startX = Mth.lerp(-1.0E-7D, start.x, end.x);
        final double startY = Mth.lerp(-1.0E-7D, start.y, end.y);
        final double startZ = Mth.lerp(-1.0E-7D, start.z, end.z);

        int currentX = Mth.floor(startX);
        int currentY = Mth.floor(startY);
        int currentZ = Mth.floor(startZ);

        final BlockPos.MutableBlockPos currentBlock = new BlockPos.MutableBlockPos(currentX, currentY, currentZ);

        LevelChunk chunk = this.getChunkIfLoaded(currentBlock);
        if (chunk == null) {
            return net.minecraft.world.phys.BlockHitResult.Type.MISS;
        }

        final net.minecraft.world.phys.BlockHitResult.Type initialCheck = this.clipDirect(start, end, currentBlock, chunk.getBlockState(currentBlock), context);
        if (initialCheck != null) {
            return initialCheck;
        }

        final double diffX = endX - startX;
        final double diffY = endY - startY;
        final double diffZ = endZ - startZ;

        final int xDirection = Mth.sign(diffX);
        final int yDirection = Mth.sign(diffY);
        final int zDirection = Mth.sign(diffZ);

        final double normalizedX = xDirection == 0 ? Double.MAX_VALUE : (double) xDirection / diffX;
        final double normalizedY = yDirection == 0 ? Double.MAX_VALUE : (double) yDirection / diffY;
        final double normalizedZ = zDirection == 0 ? Double.MAX_VALUE : (double) zDirection / diffZ;

        double normalizedXDirection = normalizedX * (xDirection > 0 ? 1.0D - Mth.frac(startX) : Mth.frac(startX));
        double normalizedYDirection = normalizedY * (yDirection > 0 ? 1.0D - Mth.frac(startY) : Mth.frac(startY));
        double normalizedZDirection = normalizedZ * (zDirection > 0 ? 1.0D - Mth.frac(startZ) : Mth.frac(startZ));

        net.minecraft.world.phys.BlockHitResult.Type result;

        do {
            if (normalizedXDirection > 1.0D && normalizedYDirection > 1.0D && normalizedZDirection > 1.0D) {
                return net.minecraft.world.phys.BlockHitResult.Type.MISS;
            }

            if (normalizedXDirection < normalizedYDirection) {
                if (normalizedXDirection < normalizedZDirection) {
                    currentX += xDirection;
                    normalizedXDirection += normalizedX;
                } else {
                    currentZ += zDirection;
                    normalizedZDirection += normalizedZ;
                }
            } else if (normalizedYDirection < normalizedZDirection) {
                currentY += yDirection;
                normalizedYDirection += normalizedY;
            } else {
                currentZ += zDirection;
                normalizedZDirection += normalizedZ;
            }

            currentBlock.set(currentX, currentY, currentZ);
            if (chunk.getPos().x != currentBlock.getX() >> 4 || chunk.getPos().z != currentBlock.getZ() >> 4) {
                chunk = this.getChunkIfLoaded(currentBlock);
                if (chunk == null) {
                    return net.minecraft.world.phys.BlockHitResult.Type.MISS;
                }
            }
            result = this.clipDirect(start, end, currentBlock, chunk.getBlockState(currentBlock), context);
        } while (result == null);

        return result;
    }
    // Paper end

    public boolean isInWorldBounds(BlockPos pos) {
        return pos.isInsideBuildHeightAndWorldBoundsHorizontal(this); // Paper - use better/optimized check
    }

    public static boolean isInSpawnableBounds(BlockPos pos) {
        return !Level.isOutsideSpawnableHeight(pos.getY()) && Level.isInWorldBoundsHorizontal(pos);
    }

    private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
        return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000; // Diff on change warnUnsafeChunk()
    }

    private static boolean isOutsideSpawnableHeight(int y) {
        return y < -20000000 || y >= 20000000;
    }

    public final LevelChunk getChunkAt(BlockPos pos) { // Paper - help inline
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public final LevelChunk getChunk(int chunkX, int chunkZ) { // Paper - final to help inline
        // Paper start - Perf: make sure loaded chunks get the inlined variant of this function
        net.minecraft.server.level.ServerChunkCache cps = ((ServerLevel)this).getChunkSource();
        LevelChunk ifLoaded = cps.getChunkAtIfLoadedImmediately(chunkX, chunkZ);
        if (ifLoaded != null) {
            return ifLoaded;
        }
        return (LevelChunk) cps.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true); // Paper - avoid a method jump
        // Paper end - Perf: make sure loaded chunks get the inlined variant of this function
    }

    // Paper start - if loaded
    @Nullable
    @Override
    public final ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        return ((ServerLevel)this).chunkSource.getChunkAtIfLoadedImmediately(x, z);
    }

    @Override
    @Nullable
    public final BlockState getBlockStateIfLoaded(BlockPos pos) {
        // CraftBukkit start - tree generation
        if (this.captureTreeGeneration) {
            CraftBlockState previous = this.capturedBlockStates.get(pos);
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            ChunkAccess chunk = this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);

            return chunk == null ? null : chunk.getBlockState(pos);
        }
    }

    @Override
    public final FluidState getFluidIfLoaded(BlockPos blockposition) {
        ChunkAccess chunk = this.getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);

        return chunk == null ? null : chunk.getFluidState(blockposition);
    }

    @Override
    public final boolean hasChunkAt(BlockPos pos) {
        return getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4) != null; // Paper - Perf: Optimize Level.hasChunkAt(BlockPosition)Z
    }

    public final boolean isLoadedAndInBounds(BlockPos blockposition) { // Paper - final for inline
        return getWorldBorder().isWithinBounds(blockposition) && getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4) != null;
    }

    public @Nullable LevelChunk getChunkIfLoaded(int x, int z) { // Overridden in WorldServer for ABI compat which has final
        return ((ServerLevel) this).getChunkSource().getChunkAtIfLoadedImmediately(x, z);
    }
    public final @Nullable LevelChunk getChunkIfLoaded(BlockPos blockposition) {
        return ((ServerLevel) this).getChunkSource().getChunkAtIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);
    }

    //  reduces need to do isLoaded before getType
    public final @Nullable BlockState getBlockStateIfLoadedAndInBounds(BlockPos blockposition) {
        return getWorldBorder().isWithinBounds(blockposition) ? getBlockStateIfLoaded(blockposition) : null;
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        // Paper end
        ChunkAccess ichunkaccess = this.getChunkSource().getChunk(chunkX, chunkZ, leastStatus, create);

        if (ichunkaccess == null && create) {
            throw new IllegalStateException("Should always be able to create a chunk!");
        } else {
            return ichunkaccess;
        }
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return this.setBlock(pos, state, flags, 512);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        // CraftBukkit start - tree generation
        if (this.captureTreeGeneration) {
            // Paper start - Protect Bedrock and End Portal/Frames from being destroyed
            BlockState type = getBlockState(pos);
            if (!type.isDestroyable()) return false;
            // Paper end - Protect Bedrock and End Portal/Frames from being destroyed
            CraftBlockState blockstate = this.capturedBlockStates.get(pos);
            if (blockstate == null) {
                blockstate = CapturedBlockState.getTreeBlockState(this, pos, flags);
                this.capturedBlockStates.put(pos.immutable(), blockstate);
            }
            blockstate.setData(state);
            blockstate.setFlag(flags);
            return true;
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        } else if (!this.isClientSide && this.isDebug()) {
            return false;
        } else {
            LevelChunk chunk = this.getChunkAt(pos);
            Block block = state.getBlock();

            // CraftBukkit start - capture blockstates
            boolean captured = false;
            if (this.captureBlockStates && !this.capturedBlockStates.containsKey(pos)) {
                CraftBlockState blockstate = (CraftBlockState) world.getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getState(); // Paper - use CB getState to get a suitable snapshot
                blockstate.setFlag(flags); // Paper - set flag
                this.capturedBlockStates.put(pos.immutable(), blockstate);
                captured = true;
            }
            // CraftBukkit end

            BlockState iblockdata1 = chunk.setBlockState(pos, state, (flags & 64) != 0, (flags & 1024) == 0); // CraftBukkit custom NO_PLACE flag
            this.chunkPacketBlockController.onBlockChange(this, pos, state, iblockdata1, flags, maxUpdateDepth); // Paper - Anti-Xray

            if (iblockdata1 == null) {
                // CraftBukkit start - remove blockstate if failed (or the same)
                if (this.captureBlockStates && captured) {
                    this.capturedBlockStates.remove(pos);
                }
                // CraftBukkit end
                return false;
            } else {
                BlockState iblockdata2 = this.getBlockState(pos);

                /*
                if (iblockdata2 == iblockdata) {
                    if (iblockdata1 != iblockdata2) {
                        this.setBlocksDirty(blockposition, iblockdata1, iblockdata2);
                    }

                    if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
                        this.sendBlockUpdated(blockposition, iblockdata1, iblockdata, i);
                    }

                    if ((i & 1) != 0) {
                        this.blockUpdated(blockposition, iblockdata1.getBlock());
                        if (!this.isClientSide && iblockdata.hasAnalogOutputSignal()) {
                            this.updateNeighbourForOutputSignal(blockposition, block);
                        }
                    }

                    if ((i & 16) == 0 && j > 0) {
                        int k = i & -34;

                        iblockdata1.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                        iblockdata.updateNeighbourShapes(this, blockposition, k, j - 1);
                        iblockdata.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                    }

                    this.onBlockStateChange(blockposition, iblockdata1, iblockdata2);
                }
                */

                // CraftBukkit start
                if (!this.captureBlockStates) { // Don't notify clients or update physics while capturing blockstates
                    // Modularize client and physic updates
                    // Spigot start
                    try {
                        this.notifyAndUpdatePhysics(pos, chunk, iblockdata1, state, iblockdata2, flags, maxUpdateDepth);
                    } catch (StackOverflowError ex) {
                        Level.lastPhysicsProblem = new BlockPos(pos);
                    }
                    // Spigot end
                }
                // CraftBukkit end

                return true;
            }
        }
    }

    // CraftBukkit start - Split off from above in order to directly send client and physic updates
    public void notifyAndUpdatePhysics(BlockPos blockposition, LevelChunk chunk, BlockState oldBlock, BlockState newBlock, BlockState actualBlock, int i, int j) {
        BlockState iblockdata = newBlock;
        BlockState iblockdata1 = oldBlock;
        BlockState iblockdata2 = actualBlock;
        if (iblockdata2 == iblockdata) {
            if (iblockdata1 != iblockdata2) {
                this.setBlocksDirty(blockposition, iblockdata1, iblockdata2);
            }

            if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk == null || (chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)))) { // allow chunk to be null here as chunk.isReady() is false when we send our notification during block placement // Paper - rewrite chunk system - change from ticking to full
                this.sendBlockUpdated(blockposition, iblockdata1, iblockdata, i);
            }

            if ((i & 1) != 0) {
                this.blockUpdated(blockposition, iblockdata1.getBlock());
                if (!this.isClientSide && iblockdata.hasAnalogOutputSignal()) {
                    this.updateNeighbourForOutputSignal(blockposition, newBlock.getBlock());
                }
            }

            if ((i & 16) == 0 && j > 0) {
                int k = i & -34;

                // CraftBukkit start
                iblockdata1.updateIndirectNeighbourShapes(this, blockposition, k, j - 1); // Don't call an event for the old block to limit event spam
                CraftWorld world = ((ServerLevel) this).getWorld();
                boolean cancelledUpdates = false; // Paper - Fix block place logic
                if (world != null && ((ServerLevel)this).hasPhysicsEvent) { // Paper - BlockPhysicsEvent
                    BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), CraftBlockData.fromData(iblockdata));
                    this.getCraftServer().getPluginManager().callEvent(event);

                    cancelledUpdates = event.isCancelled(); // Paper - Fix block place logic
                }
                // CraftBukkit end
                if (!cancelledUpdates) { // Paper - Fix block place logic
                iblockdata.updateNeighbourShapes(this, blockposition, k, j - 1);
                iblockdata.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                } // Paper - Fix block place logic
            }

            // CraftBukkit start - SPIGOT-5710
            if (!this.preventPoiUpdated) {
                this.onBlockStateChange(blockposition, iblockdata1, iblockdata2);
            }
            // CraftBukkit end
        }
    }
    // CraftBukkit end

    public void onBlockStateChange(BlockPos pos, BlockState oldBlock, BlockState newBlock) {}

    @Override
    public boolean removeBlock(BlockPos pos, boolean move) {
        FluidState fluid = this.getFluidState(pos);

        return this.setBlock(pos, fluid.createLegacyBlock(), 3 | (move ? 64 : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth) {
        BlockState iblockdata = this.getBlockState(pos);

        if (iblockdata.isAir()) {
            return false;
        } else {
            FluidState fluid = this.getFluidState(pos);
            // Paper start - BlockDestroyEvent; while the above setAir method is named same and looks very similar
            // they are NOT used with same intent and the above should not fire this event. The above method is more of a BlockSetToAirEvent,
            // it doesn't imply destruction of a block that plays a sound effect / drops an item.
            boolean playEffect = true;
            BlockState effectType = iblockdata;
            int xp = iblockdata.getBlock().getExpDrop(iblockdata, (ServerLevel) this, pos, ItemStack.EMPTY, true);
            if (com.destroystokyo.paper.event.block.BlockDestroyEvent.getHandlerList().getRegisteredListeners().length > 0) {
                com.destroystokyo.paper.event.block.BlockDestroyEvent event = new com.destroystokyo.paper.event.block.BlockDestroyEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this, pos), fluid.createLegacyBlock().createCraftBlockData(), effectType.createCraftBlockData(), xp, drop);
                if (!event.callEvent()) {
                    return false;
                }
                effectType = ((CraftBlockData) event.getEffectBlock()).getState();
                playEffect = event.playEffect();
                drop = event.willDrop();
                xp = event.getExpToDrop();
            }
            // Paper end - BlockDestroyEvent

            if (playEffect && !(effectType.getBlock() instanceof BaseFireBlock)) { // Paper - BlockDestroyEvent
                this.levelEvent(2001, pos, Block.getId(effectType)); // Paper - BlockDestroyEvent
            }

            if (drop) {
                BlockEntity tileentity = iblockdata.hasBlockEntity() ? this.getBlockEntity(pos) : null;

                Block.dropResources(iblockdata, this, pos, tileentity, breakingEntity, ItemStack.EMPTY, false); // Paper - Properly handle xp dropping
                iblockdata.getBlock().popExperience((ServerLevel) this, pos, xp, breakingEntity); // Paper - Properly handle xp dropping; custom amount
            }

            boolean flag1 = this.setBlock(pos, fluid.createLegacyBlock(), 3, maxUpdateDepth);

            if (flag1) {
                this.gameEvent((Holder) GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(breakingEntity, iblockdata));
            }

            return flag1;
        }
    }

    public void addDestroyBlockEffect(BlockPos pos, BlockState state) {}

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) {
        return this.setBlock(pos, state, 3);
    }

    public abstract void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags);

    public void setBlocksDirty(BlockPos pos, BlockState old, BlockState updated) {}

    public void updateNeighborsAt(BlockPos pos, Block sourceBlock) {}

    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block sourceBlock, Direction direction) {}

    public void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {}

    public void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {}

    @Override
    public void neighborShapeChanged(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        this.neighborUpdater.shapeUpdate(direction, neighborState, pos, neighborPos, flags, maxUpdateDepth);
    }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        int k;

        if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
            if (this.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                k = this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)).getHeight(heightmap, x & 15, z & 15) + 1;
            } else {
                k = this.getMinBuildHeight();
            }
        } else {
            k = this.getSeaLevel() + 1;
        }

        return k;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.getChunkSource().getLightEngine();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        // CraftBukkit start - tree generation
        if (this.captureTreeGeneration) {
            CraftBlockState previous = this.capturedBlockStates.get(pos); // Paper
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            ChunkAccess chunk = this.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true); // Paper - manually inline to reduce hops and avoid unnecessary null check to reduce total byte code size, this should never return null and if it does we will see it the next line but the real stack trace will matter in the chunk engine

            return chunk.getBlockState(pos);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (this.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunk chunk = this.getChunkAt(pos);

            return chunk.getFluidState(pos);
        }
    }

    public boolean isDay() {
        return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
    }

    public boolean isNight() {
        return !this.dimensionType().hasFixedTime() && !this.isDay();
    }

    public void playSound(@Nullable Entity source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
        Player entityhuman;

        if (source instanceof Player entityhuman1) {
            entityhuman = entityhuman1;
        } else {
            entityhuman = null;
        }

        this.playSound(entityhuman, pos, sound, category, volume, pitch);
    }

    @Override
    public void playSound(@Nullable Player source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSound(source, (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, sound, category, volume, pitch);
    }

    public abstract void playSeededSound(@Nullable Player source, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed);

    public void playSeededSound(@Nullable Player source, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, long seed) {
        this.playSeededSound(source, x, y, z, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch, seed);
    }

    public abstract void playSeededSound(@Nullable Player source, Entity entity, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch, long seed);

    public void playSound(@Nullable Player source, double x, double y, double z, SoundEvent sound, SoundSource category) {
        this.playSound(source, x, y, z, sound, category, 1.0F, 1.0F);
    }

    public void playSound(@Nullable Player source, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(source, x, y, z, sound, category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Player source, double x, double y, double z, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(source, x, y, z, sound, category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Player source, Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.playSeededSound(source, entity, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch, this.threadSafeRandom.nextLong());
    }

    public void playLocalSound(BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch, boolean useDistance) {
        this.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, sound, category, volume, pitch, useDistance);
    }

    public void playLocalSound(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {}

    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, boolean useDistance) {}

    @Override
    public void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public void addParticle(ParticleOptions parameters, boolean alwaysSpawn, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public void addAlwaysVisibleParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public void addAlwaysVisibleParticle(ParticleOptions parameters, boolean alwaysSpawn, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}

    public float getSunAngle(float tickDelta) {
        float f1 = this.getTimeOfDay(tickDelta);

        return f1 * 6.2831855F;
    }

    public void addBlockEntityTicker(TickingBlockEntity ticker) {
        (this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(ticker);
    }

    protected void tickBlockEntities() {
        ProfilerFiller gameprofilerfiller = this.getProfiler();

        gameprofilerfiller.push("blockEntities");
        this.timings.tileEntityPending.startTiming(); // Spigot
        this.tickingBlockEntities = true;
        if (!this.pendingBlockEntityTickers.isEmpty()) {
            this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
            this.pendingBlockEntityTickers.clear();
        }
        this.timings.tileEntityPending.stopTiming(); // Spigot

        this.timings.tileEntityTick.startTiming(); // Spigot
        // Spigot start
        // Iterator<TickingBlockEntity> iterator = this.blockEntityTickers.iterator();
        boolean flag = this.tickRateManager().runsNormally();

        int tickedEntities = 0; // Paper - rewrite chunk system

        int tilesThisCycle = 0;
        var toRemove = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<TickingBlockEntity>(); // Paper - Fix MC-117075; use removeAll
        toRemove.add(null); // Paper - Fix MC-117075
        for (tileTickPosition = 0; tileTickPosition < this.blockEntityTickers.size(); tileTickPosition++) { // Paper - Disable tick limiters
            this.tileTickPosition = (this.tileTickPosition < this.blockEntityTickers.size()) ? this.tileTickPosition : 0;
            TickingBlockEntity tickingblockentity = (TickingBlockEntity) this.blockEntityTickers.get(this.tileTickPosition);
            // Spigot end

            if (tickingblockentity.isRemoved()) {
                // Spigot start
                tilesThisCycle--;
                toRemove.add(tickingblockentity); // Paper - Fix MC-117075; use removeAll
                // Spigot end
            } else if (flag && this.shouldTickBlocksAt(tickingblockentity.getPos())) {
                tickingblockentity.tick();
                // Paper start - rewrite chunk system
                if ((++tickedEntities & 7) == 0) {
                    ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)(Level)(Object)this).moonrise$midTickTasks();
                }
                // Paper end - rewrite chunk system
            }
        }
        this.blockEntityTickers.removeAll(toRemove); // Paper - Fix MC-117075

        this.timings.tileEntityTick.stopTiming(); // Spigot
        this.tickingBlockEntities = false;
        co.aikar.timings.TimingHistory.tileEntityTicks += this.blockEntityTickers.size(); // Paper
        gameprofilerfiller.pop();
        this.spigotConfig.currentPrimedTnt = 0; // Spigot
    }

    public <T extends Entity> void guardEntityTick(Consumer<T> tickConsumer, T entity) {
        try {
            tickConsumer.accept(entity);
        } catch (Throwable throwable) {
            if (throwable instanceof ThreadDeath) throw throwable; // Paper
            // Paper start - Prevent block entity and entity crashes
            final String msg = String.format("Entity threw exception at %s:%s,%s,%s", entity.level().getWorld().getName(), entity.getX(), entity.getY(), entity.getZ());
            MinecraftServer.LOGGER.error(msg, throwable);
            getCraftServer().getPluginManager().callEvent(new com.destroystokyo.paper.event.server.ServerExceptionEvent(new com.destroystokyo.paper.exception.ServerInternalException(msg, throwable))); // Paper - ServerExceptionEvent
            entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
            // Paper end - Prevent block entity and entity crashes
        }
        this.moonrise$midTickTasks(); // Paper - rewrite chunk system
    }
    // Paper start - Option to prevent armor stands from doing entity lookups
    @Override
    public boolean noCollision(@Nullable Entity entity, AABB box) {
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand && !entity.level().paperConfig().entities.armorStands.doCollisionEntityLookups) return false;
        // Paper start - optimise collisions
        final int flags = entity == null ? (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_BORDER | ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_ONLY) : ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_FLAG_CHECK_ONLY;
        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getCollisionsForBlocksOrWorldBorder((Level)(Object)this, entity, box, null, null, flags, null)) {
            return false;
        }

        return !ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.getEntityHardCollisions((Level)(Object)this, entity, box, null, flags, null);
        // Paper end - optimise collisions
    }
    // Paper end - Option to prevent armor stands from doing entity lookups

    public boolean shouldTickDeath(Entity entity) {
        return true;
    }

    public boolean shouldTickBlocksAt(long chunkPos) {
        return true;
    }

    public boolean shouldTickBlocksAt(BlockPos pos) {
        return this.shouldTickBlocksAt(ChunkPos.asLong(pos));
    }

    public Explosion explode(@Nullable Entity entity, double x, double y, double z, float power, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, Explosion.getDefaultDamageSource(this, entity), (ExplosionDamageCalculator) null, x, y, z, power, false, explosionSourceType, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
    }

    public Explosion explode(@Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, Explosion.getDefaultDamageSource(this, entity), (ExplosionDamageCalculator) null, x, y, z, power, createFire, explosionSourceType, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, Vec3 pos, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, damageSource, behavior, pos.x(), pos.y(), pos.z(), power, createFire, explosionSourceType, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType) {
        return this.explode(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType, ParticleOptions particle, ParticleOptions emitterParticle, Holder<SoundEvent> soundEvent) {
        return this.explode(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, true, particle, emitterParticle, soundEvent);
    }

    public Explosion explode(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType, boolean particles, ParticleOptions particle, ParticleOptions emitterParticle, Holder<SoundEvent> soundEvent) {
        Explosion.BlockInteraction explosion_effect;

        switch (explosionSourceType.ordinal()) {
            case 0:
                explosion_effect = Explosion.BlockInteraction.KEEP;
                break;
            case 1:
                explosion_effect = this.getDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
                break;
            case 2:
                explosion_effect = this.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? this.getDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY) : Explosion.BlockInteraction.KEEP;
                break;
            case 3:
                explosion_effect = this.getDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
                break;
            case 4:
                explosion_effect = Explosion.BlockInteraction.TRIGGER_BLOCK;
                break;
            // CraftBukkit start - handle custom explosion type
            case 5:
                explosion_effect = Explosion.BlockInteraction.DESTROY;
                break;
            // CraftBukkit end
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Explosion.BlockInteraction explosion_effect1 = explosion_effect;
        Explosion explosion = new Explosion(this, entity, damageSource, behavior, x, y, z, power, createFire, explosion_effect1, particle, emitterParticle, soundEvent);

        explosion.explode();
        explosion.finalizeExplosion(particles);
        return explosion;
    }

    private Explosion.BlockInteraction getDestroyType(GameRules.Key<GameRules.BooleanValue> gameRuleKey) {
        return this.getGameRules().getBoolean(gameRuleKey) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    public abstract String gatherChunkSourceStats();

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // CraftBukkit start
        return this.getBlockEntity(pos, true);
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos blockposition, boolean validate) {
        // Paper start - Perf: Optimize capturedTileEntities lookup
        net.minecraft.world.level.block.entity.BlockEntity blockEntity;
        if (!this.capturedTileEntities.isEmpty() && (blockEntity = this.capturedTileEntities.get(blockposition)) != null) {
            return blockEntity;
        }
        // Paper end - Perf: Optimize capturedTileEntities lookup
        // CraftBukkit end
        return this.isOutsideBuildHeight(blockposition) ? null : (!this.isClientSide && !ca.spottedleaf.moonrise.common.util.TickThread.isTickThread() ? null : this.getChunkAt(blockposition).getBlockEntity(blockposition, LevelChunk.EntityCreationType.IMMEDIATE)); // Paper - rewrite chunk system
    }

    public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos blockposition = blockEntity.getBlockPos();

        if (!this.isOutsideBuildHeight(blockposition)) {
            // CraftBukkit start
            if (this.captureBlockStates) {
                this.capturedTileEntities.put(blockposition.immutable(), blockEntity);
                return;
            }
            // CraftBukkit end
            this.getChunkAt(blockposition).addAndRegisterBlockEntity(blockEntity);
        }
    }

    public void removeBlockEntity(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            this.getChunkAt(pos).removeBlockEntity(pos);
        }
    }

    public boolean isLoaded(BlockPos pos) {
        return this.isOutsideBuildHeight(pos) ? false : this.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    public boolean loadedAndEntityCanStandOnFace(BlockPos pos, Entity entity, Direction direction) {
        if (this.isOutsideBuildHeight(pos)) {
            return false;
        } else {
            ChunkAccess ichunkaccess = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);

            return ichunkaccess == null ? false : ichunkaccess.getBlockState(pos).entityCanStandOnFace(this, pos, entity, direction);
        }
    }

    public boolean loadedAndEntityCanStandOn(BlockPos pos, Entity entity) {
        return this.loadedAndEntityCanStandOnFace(pos, entity, Direction.UP);
    }

    public void updateSkyBrightness() {
        double d0 = 1.0D - (double) (this.getRainLevel(1.0F) * 5.0F) / 16.0D;
        double d1 = 1.0D - (double) (this.getThunderLevel(1.0F) * 5.0F) / 16.0D;
        double d2 = 0.5D + 2.0D * Mth.clamp((double) Mth.cos(this.getTimeOfDay(1.0F) * 6.2831855F), -0.25D, 0.25D);

        this.skyDarken = (int) ((1.0D - d2 * d0 * d1) * 11.0D);
    }

    public void setSpawnSettings(boolean spawnMonsters, boolean spawnAnimals) {
        this.getChunkSource().setSpawnSettings(spawnMonsters, spawnAnimals);
    }

    public BlockPos getSharedSpawnPos() {
        BlockPos blockposition = this.levelData.getSpawnPos();

        if (!this.getWorldBorder().isWithinBounds(blockposition)) {
            blockposition = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(this.getWorldBorder().getCenterX(), 0.0D, this.getWorldBorder().getCenterZ()));
        }

        return blockposition;
    }

    public float getSharedSpawnAngle() {
        return this.levelData.getSpawnAngle();
    }

    protected void prepareWeather() {
        if (this.levelData.isRaining()) {
            this.rainLevel = 1.0F;
            if (this.levelData.isThundering()) {
                this.thunderLevel = 1.0F;
            }
        }

    }

    public void close() throws IOException {
        this.getChunkSource().close();
    }

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        // Paper start - rewrite chunk system
        final List<Entity> ret = new java.util.ArrayList<>();

        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(except, box, ret, predicate);

        return ret;
        // Paper end - rewrite chunk system
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();

        this.getEntities(filter, box, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate, List<? super T> result) {
        this.getEntities(filter, box, predicate, result, Integer.MAX_VALUE);
    }

    // Paper start - rewrite chunk system
    public <T extends Entity> void getEntities(final EntityTypeTest<Entity, T> entityTypeTest,
                                               final AABB boundingBox, final Predicate<? super T> predicate,
                                               final List<? super T> into, final int maxCount) {
        this.getProfiler().incrementCounter("getEntities");

        if (entityTypeTest instanceof net.minecraft.world.entity.EntityType<T> byType) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(byType, boundingBox, into, predicate, maxCount);
                return;
            } else {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(byType, boundingBox, into, predicate);
                return;
            }
        }

        if (entityTypeTest == null) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate, maxCount);
                return;
            } else {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)predicate);
                return;
            }
        }

        final Class<? extends Entity> base = entityTypeTest.getBaseClass();

        final Predicate<? super T> modifiedPredicate;
        if (predicate == null) {
            modifiedPredicate = (final T obj) -> {
                return entityTypeTest.tryCast(obj) != null;
            };
        } else {
            modifiedPredicate = (final Entity obj) -> {
                final T casted = entityTypeTest.tryCast(obj);
                if (casted == null) {
                    return false;
                }

                return predicate.test(casted);
            };
        }

        if (base == null || base == Entity.class) {
            if (maxCount != Integer.MAX_VALUE) {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                return;
            } else {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities((Entity)null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                return;
            }
        } else {
            if (maxCount != Integer.MAX_VALUE) {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate, maxCount);
                return;
            } else {
                ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getEntities(base, null, boundingBox, (List)into, (Predicate)modifiedPredicate);
                return;
            }
        }
    }

    public org.bukkit.entity.Entity[] getChunkEntities(int chunkX, int chunkZ) {
        ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices slices = ((ServerLevel)this).moonrise$getEntityLookup().getChunk(chunkX, chunkZ);
        if (slices == null) {
            return new org.bukkit.entity.Entity[0];
        }
        return slices.getChunkEntities();
    }
    // Paper end - rewrite chunk system

    @Nullable
    public abstract Entity getEntity(int id);

    public void blockEntityChanged(BlockPos pos) {
        if (this.hasChunkAt(pos)) {
            this.getChunkAt(pos).setUnsaved(true);
        }

    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    public void disconnect() {}

    public long getGameTime() {
        return this.levelData.getGameTime();
    }

    public long getDayTime() {
        return this.levelData.getDayTime();
    }

    public boolean mayInteract(Player player, BlockPos pos) {
        return true;
    }

    public void broadcastEntityEvent(Entity entity, byte status) {}

    public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {}

    public void blockEvent(BlockPos pos, Block block, int type, int data) {
        this.getBlockState(pos).triggerEvent(this, pos, type, data);
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    public GameRules getGameRules() {
        return this.levelData.getGameRules();
    }

    public abstract TickRateManager tickRateManager();

    public float getThunderLevel(float delta) {
        return Mth.lerp(delta, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(delta);
    }

    public void setThunderLevel(float thunderGradient) {
        float f1 = Mth.clamp(thunderGradient, 0.0F, 1.0F);

        this.oThunderLevel = f1;
        this.thunderLevel = f1;
    }

    public float getRainLevel(float delta) {
        return Mth.lerp(delta, this.oRainLevel, this.rainLevel);
    }

    public void setRainLevel(float rainGradient) {
        float f1 = Mth.clamp(rainGradient, 0.0F, 1.0F);

        this.oRainLevel = f1;
        this.rainLevel = f1;
    }

    public boolean isThundering() {
        return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling() ? (double) this.getThunderLevel(1.0F) > 0.9D : false;
    }

    public boolean isRaining() {
        return (double) this.getRainLevel(1.0F) > 0.2D;
    }

    public boolean isRainingAt(BlockPos pos) {
        if (!this.isRaining()) {
            return false;
        } else if (!this.canSeeSky(pos)) {
            return false;
        } else if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            return false;
        } else {
            Biome biomebase = (Biome) this.getBiome(pos).value();

            return biomebase.getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
        }
    }

    @Nullable
    public abstract MapItemSavedData getMapData(MapId id);

    public abstract void setMapData(MapId id, MapItemSavedData state);

    public abstract MapId getFreeMapId();

    public void globalLevelEvent(int eventId, BlockPos pos, int data) {}

    public CrashReportCategory fillReportDetails(CrashReport report) {
        CrashReportCategory crashreportsystemdetails = report.addCategory("Affected level", 1);

        crashreportsystemdetails.setDetail("All players", () -> {
            int i = this.players().size();

            return "" + i + " total; " + String.valueOf(this.players());
        });
        ChunkSource ichunkprovider = this.getChunkSource();

        Objects.requireNonNull(ichunkprovider);
        crashreportsystemdetails.setDetail("Chunk stats", ichunkprovider::gatherStats);
        crashreportsystemdetails.setDetail("Level dimension", () -> {
            return this.dimension().location().toString();
        });

        try {
            this.levelData.fillCrashReportCategory(crashreportsystemdetails, this);
        } catch (Throwable throwable) {
            crashreportsystemdetails.setDetailError("Level Data Unobtainable", throwable);
        }

        return crashreportsystemdetails;
    }

    public abstract void destroyBlockProgress(int entityId, BlockPos pos, int progress);

    public void createFireworks(double x, double y, double z, double velocityX, double velocityY, double velocityZ, List<FireworkExplosion> explosions) {}

    public abstract Scoreboard getScoreboard();

    public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {
        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

        while (iterator.hasNext()) {
            Direction enumdirection = (Direction) iterator.next();
            BlockPos blockposition1 = pos.relative(enumdirection);

            if (this.hasChunkAt(blockposition1)) {
                BlockState iblockdata = this.getBlockState(blockposition1);

                if (iblockdata.is(Blocks.COMPARATOR)) {
                    this.neighborChanged(iblockdata, blockposition1, block, pos, false);
                } else if (iblockdata.isRedstoneConductor(this, blockposition1)) {
                    blockposition1 = blockposition1.relative(enumdirection);
                    iblockdata = this.getBlockState(blockposition1);
                    if (iblockdata.is(Blocks.COMPARATOR)) {
                        this.neighborChanged(iblockdata, blockposition1, block, pos, false);
                    }
                }
            }
        }

    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        long i = 0L;
        float f = 0.0F;

        if (this.hasChunkAt(pos)) {
            f = this.getMoonBrightness();
            i = this.getChunkAt(pos).getInhabitedTime();
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), i, f);
    }

    @Override
    public int getSkyDarken() {
        return this.skyDarken;
    }

    public void setSkyFlashTime(int lightningTicksLeft) {}

    @Override
    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    public void sendPacketToServer(Packet<?> packet) {
        throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
    }

    @Override
    public DimensionType dimensionType() {
        return (DimensionType) this.dimensionTypeRegistration.value();
    }

    public Holder<DimensionType> dimensionTypeRegistration() {
        return this.dimensionTypeRegistration;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(this.getBlockState(pos));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) {
        return state.test(this.getFluidState(pos));
    }

    public abstract RecipeManager getRecipeManager();

    public BlockPos getBlockRandomPos(int x, int y, int z, int l) {
        this.randValue = this.randValue * 3 + 1013904223;
        int i1 = this.randValue >> 2;

        return new BlockPos(x + (i1 & 15), y + (i1 >> 16 & l), z + (i1 >> 8 & 15));
    }

    public boolean noSave() {
        return false;
    }

    public ProfilerFiller getProfiler() {
        return (ProfilerFiller) this.profiler.get();
    }

    public Supplier<ProfilerFiller> getProfilerSupplier() {
        return this.profiler;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    public final boolean isDebug() {
        return this.isDebug;
    }

    public abstract LevelEntityGetter<Entity> getEntities();

    @Override
    public long nextSubTickCount() {
        return (long) (this.subTickCount++);
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public DamageSources damageSources() {
        return this.damageSources;
    }

    public abstract PotionBrewing potionBrewing();

    public static enum ExplosionInteraction implements StringRepresentable {

        NONE("none"), BLOCK("block"), MOB("mob"), TNT("tnt"), TRIGGER("trigger"), STANDARD("standard"); // CraftBukkit - Add STANDARD which will always use Explosion.Effect.DESTROY

        public static final Codec<Level.ExplosionInteraction> CODEC = StringRepresentable.fromEnum(Level.ExplosionInteraction::values);
        private final String id;

        private ExplosionInteraction(String s) {
            this.id = s;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }
    // Paper start - respect global sound events gamerule
    public List<net.minecraft.server.level.ServerPlayer> getPlayersForGlobalSoundGamerule() {
        return this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS) ? ((ServerLevel) this).getServer().getPlayerList().players : ((ServerLevel) this).players();
    }

    public double getGlobalSoundRangeSquared(java.util.function.Function<org.spigotmc.SpigotWorldConfig, Integer> rangeFunction) {
        final double range = rangeFunction.apply(this.spigotConfig);
        return range <= 0 ? 64.0 * 64.0 : range * range; // 64 is taken from default in ServerLevel#levelEvent
    }
    // Paper end - respect global sound events gamerule
    // Paper start - notify observers even if grow failed
    public void checkCapturedTreeStateForObserverNotify(final BlockPos pos, final CraftBlockState craftBlockState) {
        // notify observers if the block state is the same and the Y level equals the original y level (for mega trees)
        // blocks at the same Y level with the same state can be assumed to be saplings which trigger observers regardless of if the
        // tree grew or not
        if (craftBlockState.getPosition().getY() == pos.getY() && this.getBlockState(craftBlockState.getPosition()) == craftBlockState.getHandle()) {
            this.notifyAndUpdatePhysics(craftBlockState.getPosition(), null, craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getHandle(), craftBlockState.getFlag(), 512);
        }
    }
    // Paper end - notify observers even if grow failed
    // Paper start - optimize redstone (Alternate Current)
    public alternate.current.wire.WireHandler getWireHandler() {
        // This method is overridden in ServerLevel.
        // Since Paper is a server platform there is no risk
        // of this implementation being called. It is here
        // only so this method can be called without casting
        // an instance of Level to ServerLevel.
        return null;
    }
    // Paper end - optimize redstone (Alternate Current)
}
