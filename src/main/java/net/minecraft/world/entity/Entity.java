package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityUnleashEvent.UnleashReason;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.PluginManager;
// CraftBukkit end

public abstract class Entity implements SyncedDataHolder, Nameable, EntityAccess, CommandSource, ScoreHolder {

    // CraftBukkit start
    private static final int CURRENT_LEVEL = 2;
    public boolean preserveMotion = true; // Paper - Fix Entity Teleportation and cancel velocity if teleported; keep initial motion on first setPositionRotation
    static boolean isLevelAtLeast(CompoundTag tag, int level) {
        return tag.contains("Bukkit.updateLevel") && tag.getInt("Bukkit.updateLevel") >= level;
    }

    // Paper start - Share random for entities to make them more random
    public static RandomSource SHARED_RANDOM = new RandomRandomSource();
    private static final class RandomRandomSource extends java.util.Random implements net.minecraft.world.level.levelgen.BitRandomSource {
        private boolean locked = false;

        @Override
        public synchronized void setSeed(long seed) {
            if (locked) {
                LOGGER.error("Ignoring setSeed on Entity.SHARED_RANDOM", new Throwable());
            } else {
                super.setSeed(seed);
                locked = true;
            }
        }

        @Override
        public RandomSource fork() {
            return new net.minecraft.world.level.levelgen.LegacyRandomSource(this.nextLong());
        }

        @Override
        public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            return new net.minecraft.world.level.levelgen.LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong());
        }

        // these below are added to fix reobf issues that I don't wanna deal with right now
        @Override
        public int next(int bits) {
            return super.next(bits);
        }

        @Override
        public int nextInt(int origin, int bound) {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextInt(origin, bound);
        }

        @Override
        public long nextLong() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextLong();
        }

        @Override
        public int nextInt() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextInt(bound);
        }

        @Override
        public boolean nextBoolean() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextFloat();
        }

        @Override
        public double nextDouble() {
            return net.minecraft.world.level.levelgen.BitRandomSource.super.nextDouble();
        }

        @Override
        public double nextGaussian() {
            return super.nextGaussian();
        }
    }
    // Paper end - Share random for entities to make them more random
    public org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason; // Paper - Entity#getEntitySpawnReason

    private CraftEntity bukkitEntity;

    public CraftEntity getBukkitEntity() {
        if (this.bukkitEntity == null) {
            this.bukkitEntity = CraftEntity.getEntity(this.level.getCraftServer(), this);
        }
        return this.bukkitEntity;
    }

    @Override
    public CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return this.getBukkitEntity();
    }

    // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    public int getDefaultMaxAirSupply() {
        return Entity.TOTAL_AIR_SUPPLY;
    }
    // CraftBukkit end

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID_TAG = "id";
    public static final String PASSENGERS_TAG = "Passengers";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    public static final int CONTENTS_SLOT_INDEX = 0;
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001D;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999D;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    public static final int BASE_SAFE_FALL_DISTANCE = 3;
    private static final AABB INITIAL_AABB = new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    private static final double WATER_FLOW_SCALE = 0.014D;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007D;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335D;
    public static final String UUID_TAG = "UUID";
    private static double viewScale = 1.0D;
    private final EntityType<?> type;
    private int id;
    public boolean blocksBuilding;
    public ImmutableList<Entity> passengers;
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    private Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    private BlockPos blockPosition;
    private ChunkPos chunkPosition;
    private Vec3 deltaMovement;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb;
    public boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier;
    @Nullable
    private Entity.RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float walkDistO;
    public float walkDist;
    public float moveDist;
    public float flyDist;
    public float fallDistance;
    private float nextStep;
    public double xOld;
    public double yOld;
    public double zOld;
    public boolean noPhysics;
    public final RandomSource random;
    public int tickCount;
    private int remainingFireTicks;
    public boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    protected boolean wasEyeInWater;
    private final Set<TagKey<Fluid>> fluidOnEyes;
    public int invulnerableTime;
    protected boolean firstTick;
    protected final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    public static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT);
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<net.minecraft.world.entity.Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
    private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private EntityInLevelCallback levelCallback;
    private final VecDeltaCodec packetPositionCodec;
    public boolean noCulling;
    public boolean hasImpulse;
    @Nullable
    public PortalProcessor portalProcess;
    public int portalCooldown;
    private boolean invulnerable;
    protected UUID uuid;
    protected String stringUUID;
    private boolean hasGlowingTag;
    private final Set<String> tags;
    private final double[] pistonDeltas;
    private long pistonDeltasGameTime;
    private EntityDimensions dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public boolean wasOnFire;
    public Optional<BlockPos> mainSupportingBlockPos;
    private boolean onGroundNoBlocks;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    public boolean hasVisualFire;
    @Nullable
    private BlockState inBlockState;
    // CraftBukkit start
    public boolean forceDrops;
    public boolean persist = true;
    public boolean visibleByDefault = true;
    public boolean valid;
    public boolean inWorld = false;
    public boolean generation;
    public int maxAirTicks = this.getDefaultMaxAirSupply(); // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    public org.bukkit.projectiles.ProjectileSource projectileSource; // For projectiles only
    public boolean lastDamageCancelled; // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Keep track if the event was canceled
    public boolean persistentInvisibility = false;
    public BlockPos lastLavaContact;
    // Marks an entity, that it was removed by a plugin via Entity#remove
    // Main use case currently is for SPIGOT-7487, preventing dropping of leash when leash is removed
    public boolean pluginRemoved = false;
    // Spigot start
    public final org.spigotmc.ActivationRange.ActivationType activationType = org.spigotmc.ActivationRange.initializeEntityActivationType(this);
    public final boolean defaultActivationState;
    public long activatedTick = Integer.MIN_VALUE;
    public void inactiveTick() { }
    // Spigot end
    protected int numCollisions = 0; // Paper - Cap entity collisions
    public boolean fromNetherPortal; // Paper - Add option to nerf pigmen from nether portals
    public boolean spawnedViaMobSpawner; // Paper - Yes this name is similar to above, upstream took the better one
    // Paper start - Entity origin API
    @javax.annotation.Nullable
    private org.bukkit.util.Vector origin;
    @javax.annotation.Nullable
    private UUID originWorld;

    public void setOrigin(@javax.annotation.Nonnull Location location) {
        this.origin = location.toVector();
        this.originWorld = location.getWorld().getUID();
    }

    @javax.annotation.Nullable
    public org.bukkit.util.Vector getOriginVector() {
        return this.origin != null ? this.origin.clone() : null;
    }

    @javax.annotation.Nullable
    public UUID getOriginWorld() {
        return this.originWorld;
    }
    // Paper end - Entity origin API
    public float getBukkitYaw() {
        return this.yRot;
    }

    public boolean isChunkLoaded() {
        return this.level.hasChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4);
    }
    // CraftBukkit end
    // Paper start
    public final AABB getBoundingBoxAt(double x, double y, double z) {
        return this.dimensions.makeBoundingBox(x, y, z);
    }
    // Paper end

    public Entity(EntityType<?> type, Level world) {
        this.id = Entity.ENTITY_COUNTER.incrementAndGet();
        this.passengers = ImmutableList.of();
        this.deltaMovement = Vec3.ZERO;
        this.bb = Entity.INITIAL_AABB;
        this.stuckSpeedMultiplier = Vec3.ZERO;
        this.nextStep = 1.0F;
        this.random = SHARED_RANDOM; // Paper - Share random for entities to make them more random
        this.remainingFireTicks = -this.getFireImmuneTicks();
        this.fluidHeight = new Object2DoubleArrayMap(2);
        this.fluidOnEyes = new HashSet();
        this.firstTick = true;
        this.levelCallback = EntityInLevelCallback.NULL;
        this.packetPositionCodec = new VecDeltaCodec();
        this.uuid = Mth.createInsecureUUID(this.random);
        this.stringUUID = this.uuid.toString();
        this.tags = Sets.newHashSet();
        this.pistonDeltas = new double[]{0.0D, 0.0D, 0.0D};
        this.mainSupportingBlockPos = Optional.empty();
        this.onGroundNoBlocks = false;
        this.inBlockState = null;
        this.type = type;
        this.level = world;
        this.dimensions = type.getDimensions();
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.chunkPosition = ChunkPos.ZERO;
        // Spigot start
        if (world != null) {
            this.defaultActivationState = org.spigotmc.ActivationRange.initializeEntityActivationState(this, world.spigotConfig);
        } else {
            this.defaultActivationState = false;
        }
        // Spigot end
        SynchedEntityData.Builder datawatcher_a = new SynchedEntityData.Builder(this);

        datawatcher_a.define(Entity.DATA_SHARED_FLAGS_ID, (byte) 0);
        datawatcher_a.define(Entity.DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        datawatcher_a.define(Entity.DATA_CUSTOM_NAME_VISIBLE, false);
        datawatcher_a.define(Entity.DATA_CUSTOM_NAME, Optional.empty());
        datawatcher_a.define(Entity.DATA_SILENT, false);
        datawatcher_a.define(Entity.DATA_NO_GRAVITY, false);
        datawatcher_a.define(Entity.DATA_POSE, net.minecraft.world.entity.Pose.STANDING);
        datawatcher_a.define(Entity.DATA_TICKS_FROZEN, 0);
        this.defineSynchedData(datawatcher_a);
        this.entityData = datawatcher_a.build();
        this.setPos(0.0D, 0.0D, 0.0D);
        this.eyeHeight = this.dimensions.eyeHeight();
    }

    public boolean isColliding(BlockPos pos, BlockState state) {
        VoxelShape voxelshape = state.getCollisionShape(this.level(), pos, CollisionContext.of(this));
        VoxelShape voxelshape1 = voxelshape.move((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());

        return Shapes.joinIsNotEmpty(voxelshape1, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
    }

    public int getTeamColor() {
        PlayerTeam scoreboardteam = this.getTeam();

        return scoreboardteam != null && scoreboardteam.getColor().getColor() != null ? scoreboardteam.getColor().getColor() : 16777215;
    }

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }

    }

    public void syncPacketPositionCodec(double x, double y, double z) {
        this.packetPositionCodec.setBase(new Vec3(x, y, z));
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String tag) {
        return this.tags.size() >= 1024 ? false : this.tags.add(tag);
    }

    public boolean removeTag(String tag) {
        return this.tags.remove(tag);
    }

    public void kill() {
        this.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        // CraftBukkit start - add Bukkit remove cause
        this.discard(null);
    }

    public final void discard(EntityRemoveEvent.Cause cause) {
        this.remove(Entity.RemovalReason.DISCARDED, cause);
        // CraftBukkit end
    }

    protected abstract void defineSynchedData(SynchedEntityData.Builder builder);

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    // CraftBukkit start
    public void refreshEntityData(ServerPlayer to) {
        List<SynchedEntityData.DataValue<?>> list = this.getEntityData().getNonDefaultValues();

        if (list != null) {
            to.connection.send(new ClientboundSetEntityDataPacket(this.getId(), list));
        }
    }
    // CraftBukkit end

    public boolean equals(Object object) {
        return object instanceof Entity ? ((Entity) object).id == this.id : false;
    }

    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.setRemoved(reason, null);
    }

    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        this.setRemoved(entity_removalreason, cause);
        // CraftBukkit end
    }

    public void onClientRemoval() {}

    public void setPose(net.minecraft.world.entity.Pose pose) {
        // CraftBukkit start
        if (pose == this.getPose()) {
            return;
        }
        this.level.getCraftServer().getPluginManager().callEvent(new EntityPoseChangeEvent(this.getBukkitEntity(), Pose.values()[pose.ordinal()]));
        // CraftBukkit end
        this.entityData.set(Entity.DATA_POSE, pose);
    }

    public net.minecraft.world.entity.Pose getPose() {
        return (net.minecraft.world.entity.Pose) this.entityData.get(Entity.DATA_POSE);
    }

    public boolean hasPose(net.minecraft.world.entity.Pose pose) {
        return this.getPose() == pose;
    }

    public boolean closerThan(Entity entity, double radius) {
        return this.position().closerThan(entity.position(), radius);
    }

    public boolean closerThan(Entity entity, double horizontalRadius, double verticalRadius) {
        double d2 = entity.getX() - this.getX();
        double d3 = entity.getY() - this.getY();
        double d4 = entity.getZ() - this.getZ();

        return Mth.lengthSquared(d2, d4) < Mth.square(horizontalRadius) && Mth.square(d3) < Mth.square(verticalRadius);
    }

    public void setRot(float yaw, float pitch) {
        // CraftBukkit start - yaw was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(yaw)) {
            yaw = 0;
        }

        if (yaw == Float.POSITIVE_INFINITY || yaw == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid yaw");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite yaw (Hacking?)");
            }
            yaw = 0;
        }

        // pitch was sometimes set to NaN, so we need to set it back to 0
        if (Float.isNaN(pitch)) {
            pitch = 0;
        }

        if (pitch == Float.POSITIVE_INFINITY || pitch == Float.NEGATIVE_INFINITY) {
            if (this instanceof ServerPlayer) {
                this.level.getCraftServer().getLogger().warning(this.getScoreboardName() + " was caught trying to crash the server with an invalid pitch");
                ((CraftPlayer) this.getBukkitEntity()).kickPlayer("Infinite pitch (Hacking?)");
            }
            pitch = 0;
        }
        // CraftBukkit end

        this.setYRot(yaw % 360.0F);
        this.setXRot(pitch % 360.0F);
    }

    public final void setPos(Vec3 pos) {
        this.setPos(pos.x(), pos.y(), pos.z());
    }

    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z, true); // Paper - Block invalid positions and bounding box; force update
        // this.setBoundingBox(this.makeBoundingBox()); // Paper - Block invalid positions and bounding box; move into setPosRaw
    }

    protected AABB makeBoundingBox() {
        return this.dimensions.makeBoundingBox(this.position);
    }

    protected void reapplyPosition() {
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void turn(double cursorDeltaX, double cursorDeltaY) {
        float f = (float) cursorDeltaY * 0.15F;
        float f1 = (float) cursorDeltaX * 0.15F;

        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + f1);
        this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += f1;
        this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }

    }

    public void tick() {
        this.baseTick();
    }

    // CraftBukkit start
    public void postTick() {
        // No clean way to break out of ticking once the entity has been copied to a new world, so instead we move the portalling later in the tick cycle
        if (!(this instanceof ServerPlayer)) {
            this.handlePortal();
        }
    }
    // CraftBukkit end

    public void baseTick() {
        this.level().getProfiler().push("entityBaseTick");
        this.inBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            --this.boardingCooldown;
        }

        this.walkDistO = this.walkDist;
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        if (this instanceof ServerPlayer) this.handlePortal(); // CraftBukkit - // Moved up to postTick
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        if (this.level().isClientSide) {
            this.clearFire();
        } else if (this.remainingFireTicks > 0) {
            if (this.fireImmune()) {
                this.setRemainingFireTicks(this.remainingFireTicks - 4);
                if (this.remainingFireTicks < 0) {
                    this.clearFire();
                }
            } else {
                if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                    this.hurt(this.damageSources().onFire(), 1.0F);
                }

                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            }

            if (this.getTicksFrozen() > 0) {
                this.setTicksFrozen(0);
                this.level().levelEvent((Player) null, 1009, this.blockPosition, 1);
            }
        }

        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
            // CraftBukkit start
        } else {
            this.lastLavaContact = null;
            // CraftBukkit end
        }

        this.checkBelowWorld();
        if (!this.level().isClientSide) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        if (!this.level().isClientSide && this instanceof Leashable) {
            Leashable.tickLeash((Entity & Leashable) this); // CraftBukkit - decompile error
        }

        this.level().getProfiler().pop();
    }

    public void setSharedFlagOnFire(boolean onFire) {
        this.setSharedFlag(0, onFire || this.hasVisualFire);
    }

    public void checkBelowWorld() {
        // Paper start - Configurable nether ceiling damage
        if (this.getY() < (double) (this.level.getMinBuildHeight() - 64) || (this.level.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER
            && this.level.paperConfig().environment.netherCeilingVoidDamageHeight.test(v -> this.getY() >= v)
            && (!(this instanceof Player player) || !player.getAbilities().invulnerable))) {
            // Paper end - Configurable nether ceiling damage
            this.onBelowWorld();
        }

    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            --this.portalCooldown;
        }

    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            // CraftBukkit start - Fallen in lava TODO: this event spams!
            if (this instanceof net.minecraft.world.entity.LivingEntity && this.remainingFireTicks <= 0) {
                // not on fire yet
                org.bukkit.block.Block damager = (this.lastLavaContact == null) ? null : org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.lastLavaContact);
                org.bukkit.entity.Entity damagee = this.getBukkitEntity();
                EntityCombustEvent combustEvent = new org.bukkit.event.entity.EntityCombustByBlockEvent(damager, damagee, 15);
                this.level.getCraftServer().getPluginManager().callEvent(combustEvent);

                if (!combustEvent.isCancelled()) {
                    this.igniteForSeconds(combustEvent.getDuration(), false);
                }
            } else {
                // This will be called every single tick the entity is in lava, so don't throw an event
                this.igniteForSeconds(15.0F, false);
            }

            if (this.hurt(this.damageSources().lava().directBlock(this.level, this.lastLavaContact), 4.0F)) {
                this.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
            }
            // CraftBukkit end - we also don't throw an event unless the object in lava is living, to save on some event calls

        }
    }

    public final void igniteForSeconds(float seconds) {
        // CraftBukkit start
        this.igniteForSeconds(seconds, true);
    }

    public final void igniteForSeconds(float f, boolean callEvent) {
        if (callEvent) {
            EntityCombustEvent event = new EntityCombustEvent(this.getBukkitEntity(), f);
            this.level.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            f = event.getDuration();
        }
        // CraftBukkit end
        this.igniteForTicks(Mth.floor(f * 20.0F));
    }

    public void igniteForTicks(int ticks) {
        if (this.remainingFireTicks < ticks) {
            this.setRemainingFireTicks(ticks);
        }

    }

    public void setRemainingFireTicks(int fireTicks) {
        this.remainingFireTicks = fireTicks;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void onBelowWorld() {
        this.discard(EntityRemoveEvent.Cause.OUT_OF_WORLD); // CraftBukkit - add Bukkit remove cause
    }

    public boolean isFree(double offsetX, double offsetY, double offsetZ) {
        return this.isFree(this.getBoundingBox().move(offsetX, offsetY, offsetZ));
    }

    private boolean isFree(AABB box) {
        return this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box);
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, (Vec3) null);
    }

    public void setOnGroundWithMovement(boolean onGround, Vec3 movement) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, movement);
    }

    public boolean isSupportedBy(BlockPos pos) {
        return this.mainSupportingBlockPos.isPresent() && ((BlockPos) this.mainSupportingBlockPos.get()).equals(pos);
    }

    protected void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement) {
        if (onGround) {
            AABB axisalignedbb = this.getBoundingBox();
            AABB axisalignedbb1 = new AABB(axisalignedbb.minX, axisalignedbb.minY - 1.0E-6D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
            Optional<BlockPos> optional = this.level.findSupportingBlock(this, axisalignedbb1);

            if (!optional.isPresent() && !this.onGroundNoBlocks) {
                if (movement != null) {
                    AABB axisalignedbb2 = axisalignedbb1.move(-movement.x, 0.0D, -movement.z);

                    optional = this.level.findSupportingBlock(this, axisalignedbb2);
                    this.mainSupportingBlockPos = optional;
                }
            } else {
                this.mainSupportingBlockPos = optional;
            }

            this.onGroundNoBlocks = optional.isEmpty();
        } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
                this.mainSupportingBlockPos = Optional.empty();
            }
        }

    }

    public boolean onGround() {
        return this.onGround;
    }

    public void move(MoverType movementType, Vec3 movement) {
        if (this.noPhysics) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
        } else {
            this.wasOnFire = this.isOnFire();
            if (movementType == MoverType.PISTON) {
                movement = this.limitPistonMovement(movement);
                if (movement.equals(Vec3.ZERO)) {
                    return;
                }
            }

            this.level().getProfiler().push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7D) {
                movement = movement.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }

            movement = this.maybeBackOffFromEdge(movement, movementType);
            Vec3 vec3d1 = this.collide(movement);
            double d0 = vec3d1.lengthSqr();

            if (d0 > 1.0E-7D) {
                if (this.fallDistance != 0.0F && d0 >= 1.0D) {
                    BlockHitResult movingobjectpositionblock = this.level().clip(new ClipContext(this.position(), this.position().add(vec3d1), ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this));

                    if (movingobjectpositionblock.getType() != HitResult.Type.MISS) {
                        this.resetFallDistance();
                    }
                }

                this.setPos(this.getX() + vec3d1.x, this.getY() + vec3d1.y, this.getZ() + vec3d1.z);
            }

            this.level().getProfiler().pop();
            this.level().getProfiler().push("rest");
            boolean flag = !Mth.equal(movement.x, vec3d1.x);
            boolean flag1 = !Mth.equal(movement.z, vec3d1.z);

            this.horizontalCollision = flag || flag1;
            this.verticalCollision = movement.y != vec3d1.y;
            this.verticalCollisionBelow = this.verticalCollision && movement.y < 0.0D;
            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec3d1);
            } else {
                this.minorHorizontalCollision = false;
            }

            this.setOnGroundWithMovement(this.verticalCollisionBelow, vec3d1);
            BlockPos blockposition = this.getOnPosLegacy();
            BlockState iblockdata = this.level().getBlockState(blockposition);

            this.checkFallDamage(vec3d1.y, this.onGround(), iblockdata, blockposition);
            if (this.isRemoved()) {
                this.level().getProfiler().pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3 vec3d2 = this.getDeltaMovement();

                    this.setDeltaMovement(flag ? 0.0D : vec3d2.x, vec3d2.y, flag1 ? 0.0D : vec3d2.z);
                }

                Block block = iblockdata.getBlock();

                if (movement.y != vec3d1.y) {
                    block.updateEntityAfterFallOn(this.level(), this);
                }

                // CraftBukkit start
                if (this.horizontalCollision && this.getBukkitEntity() instanceof Vehicle) {
                    Vehicle vehicle = (Vehicle) this.getBukkitEntity();
                    org.bukkit.block.Block bl = this.level.getWorld().getBlockAt(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()));

                    if (movement.x > vec3d1.x) {
                        bl = bl.getRelative(BlockFace.EAST);
                    } else if (movement.x < vec3d1.x) {
                        bl = bl.getRelative(BlockFace.WEST);
                    } else if (movement.z > vec3d1.z) {
                        bl = bl.getRelative(BlockFace.SOUTH);
                    } else if (movement.z < vec3d1.z) {
                        bl = bl.getRelative(BlockFace.NORTH);
                    }

                    if (!bl.getType().isAir()) {
                        VehicleBlockCollisionEvent event = new VehicleBlockCollisionEvent(vehicle, bl);
                        this.level.getCraftServer().getPluginManager().callEvent(event);
                    }
                }
                // CraftBukkit end

                if (this.onGround()) {
                    block.stepOn(this.level(), blockposition, iblockdata, this);
                }

                Entity.MovementEmission entity_movementemission = this.getMovementEmission();

                if (entity_movementemission.emitsAnything() && !this.isPassenger()) {
                    double d1 = vec3d1.x;
                    double d2 = vec3d1.y;
                    double d3 = vec3d1.z;

                    this.flyDist += (float) (vec3d1.length() * 0.6D);
                    BlockPos blockposition1 = this.getOnPos();
                    BlockState iblockdata1 = this.level().getBlockState(blockposition1);
                    boolean flag2 = this.isStateClimbable(iblockdata1);

                    if (!flag2) {
                        d2 = 0.0D;
                    }

                    this.walkDist += (float) vec3d1.horizontalDistance() * 0.6F;
                    this.moveDist += (float) Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3) * 0.6F;
                    if (this.moveDist > this.nextStep && !iblockdata1.isAir()) {
                        boolean flag3 = blockposition1.equals(blockposition);
                        boolean flag4 = this.vibrationAndSoundEffectsFromBlock(blockposition, iblockdata, entity_movementemission.emitsSounds(), flag3, movement);

                        if (!flag3) {
                            flag4 |= this.vibrationAndSoundEffectsFromBlock(blockposition1, iblockdata1, false, entity_movementemission.emitsEvents(), movement);
                        }

                        if (flag4) {
                            this.nextStep = this.nextStep();
                        } else if (this.isInWater()) {
                            this.nextStep = this.nextStep();
                            if (entity_movementemission.emitsSounds()) {
                                this.waterSwimSound();
                            }

                            if (entity_movementemission.emitsEvents()) {
                                this.gameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (iblockdata1.isAir()) {
                        this.processFlappingMovement();
                    }
                }

                this.tryCheckInsideBlocks();
                float f = this.getBlockSpeedFactor();

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 1.0D, (double) f));
                if (this.level().getBlockStatesIfLoaded(this.getBoundingBox().deflate(1.0E-6D)).noneMatch((iblockdata2) -> {
                    return iblockdata2.is(BlockTags.FIRE) || iblockdata2.is(Blocks.LAVA);
                })) {
                    if (this.remainingFireTicks <= 0) {
                        this.setRemainingFireTicks(-this.getFireImmuneTicks());
                    }

                    if (this.wasOnFire && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                        this.playEntityOnFireExtinguishedSound();
                    }
                }

                if (this.isOnFire() && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                    this.setRemainingFireTicks(-this.getFireImmuneTicks());
                }

                this.level().getProfiler().pop();
            }
        }
    }

    private boolean isStateClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE) || state.is(Blocks.POWDER_SNOW);
    }

    private boolean vibrationAndSoundEffectsFromBlock(BlockPos pos, BlockState state, boolean playSound, boolean emitEvent, Vec3 movement) {
        if (state.isAir()) {
            return false;
        } else {
            boolean flag2 = this.isStateClimbable(state);

            if ((this.onGround() || flag2 || this.isCrouching() && movement.y == 0.0D || this.isOnRails()) && !this.isSwimming()) {
                if (playSound) {
                    this.walkingStepSound(pos, state);
                }

                if (emitEvent) {
                    this.level().gameEvent((Holder) GameEvent.STEP, this.position(), GameEvent.Context.of(this, state));
                }

                return true;
            } else {
                return false;
            }
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3 adjustedMovement) {
        return false;
    }

    protected void tryCheckInsideBlocks() {
        try {
            this.checkInsideBlocks();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Checking entity block collision");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being checked for collision");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    protected void playEntityOnFireExtinguishedSound() {
        this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    public void extinguishFire() {
        if (!this.level().isClientSide && this.wasOnFire) {
            this.playEntityOnFireExtinguishedSound();
        }

        this.clearFire();
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }

    }

    /** @deprecated */
    @Deprecated
    public BlockPos getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.500001F);
    }

    public BlockPos getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    protected BlockPos getOnPos(float offset) {
        if (this.mainSupportingBlockPos.isPresent()) {
            BlockPos blockposition = (BlockPos) this.mainSupportingBlockPos.get();

            if (offset <= 1.0E-5F) {
                return blockposition;
            } else {
                BlockState iblockdata = this.level().getBlockState(blockposition);

                return ((double) offset > 0.5D || !iblockdata.is(BlockTags.FENCES)) && !iblockdata.is(BlockTags.WALLS) && !(iblockdata.getBlock() instanceof FenceGateBlock) ? blockposition.atY(Mth.floor(this.position.y - (double) offset)) : blockposition;
            }
        } else {
            int i = Mth.floor(this.position.x);
            int j = Mth.floor(this.position.y - (double) offset);
            int k = Mth.floor(this.position.z);

            return new BlockPos(i, j, k);
        }
    }

    protected float getBlockJumpFactor() {
        float f = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float f1 = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();

        return (double) f == 1.0D ? f1 : f;
    }

    protected float getBlockSpeedFactor() {
        BlockState iblockdata = this.level().getBlockState(this.blockPosition());
        float f = iblockdata.getBlock().getSpeedFactor();

        return !iblockdata.is(Blocks.WATER) && !iblockdata.is(Blocks.BUBBLE_COLUMN) ? ((double) f == 1.0D ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f) : f;
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 movement, MoverType type) {
        return movement;
    }

    protected Vec3 limitPistonMovement(Vec3 movement) {
        if (movement.lengthSqr() <= 1.0E-7D) {
            return movement;
        } else {
            long i = this.level().getGameTime();

            if (i != this.pistonDeltasGameTime) {
                Arrays.fill(this.pistonDeltas, 0.0D);
                this.pistonDeltasGameTime = i;
            }

            double d0;

            if (movement.x != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.X, movement.x);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(d0, 0.0D, 0.0D);
            } else if (movement.y != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.Y, movement.y);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(0.0D, d0, 0.0D);
            } else if (movement.z != 0.0D) {
                d0 = this.applyPistonMovementRestriction(Direction.Axis.Z, movement.z);
                return Math.abs(d0) <= 9.999999747378752E-6D ? Vec3.ZERO : new Vec3(0.0D, 0.0D, d0);
            } else {
                return Vec3.ZERO;
            }
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis axis, double offsetFactor) {
        int i = axis.ordinal();
        double d1 = Mth.clamp(offsetFactor + this.pistonDeltas[i], -0.51D, 0.51D);

        offsetFactor = d1 - this.pistonDeltas[i];
        this.pistonDeltas[i] = d1;
        return offsetFactor;
    }

    private Vec3 collide(Vec3 movement) {
        AABB axisalignedbb = this.getBoundingBox();
        List<VoxelShape> list = this.level().getEntityCollisions(this, axisalignedbb.expandTowards(movement));
        Vec3 vec3d1 = movement.lengthSqr() == 0.0D ? movement : Entity.collideBoundingBox(this, movement, axisalignedbb, this.level(), list);
        boolean flag = movement.x != vec3d1.x;
        boolean flag1 = movement.y != vec3d1.y;
        boolean flag2 = movement.z != vec3d1.z;
        boolean flag3 = flag1 && movement.y < 0.0D;

        if (this.maxUpStep() > 0.0F && (flag3 || this.onGround()) && (flag || flag2)) {
            AABB axisalignedbb1 = flag3 ? axisalignedbb.move(0.0D, vec3d1.y, 0.0D) : axisalignedbb;
            AABB axisalignedbb2 = axisalignedbb1.expandTowards(movement.x, (double) this.maxUpStep(), movement.z);

            if (!flag3) {
                axisalignedbb2 = axisalignedbb2.expandTowards(0.0D, -9.999999747378752E-6D, 0.0D);
            }

            List<VoxelShape> list1 = Entity.collectColliders(this, this.level, list, axisalignedbb2);
            float f = (float) vec3d1.y;
            float[] afloat = Entity.collectCandidateStepUpHeights(axisalignedbb1, list1, this.maxUpStep(), f);
            float[] afloat1 = afloat;
            int i = afloat.length;

            for (int j = 0; j < i; ++j) {
                float f1 = afloat1[j];
                Vec3 vec3d2 = Entity.collideWithShapes(new Vec3(movement.x, (double) f1, movement.z), axisalignedbb1, list1);

                if (vec3d2.horizontalDistanceSqr() > vec3d1.horizontalDistanceSqr()) {
                    double d0 = axisalignedbb.minY - axisalignedbb1.minY;

                    return vec3d2.add(0.0D, -d0, 0.0D);
                }
            }
        }

        return vec3d1;
    }

    private static float[] collectCandidateStepUpHeights(AABB collisionBox, List<VoxelShape> collisions, float f, float stepHeight) {
        FloatArraySet floatarrayset = new FloatArraySet(4);
        Iterator iterator = collisions.iterator();

        while (iterator.hasNext()) {
            VoxelShape voxelshape = (VoxelShape) iterator.next();
            DoubleList doublelist = voxelshape.getCoords(Direction.Axis.Y);
            DoubleListIterator doublelistiterator = doublelist.iterator();

            while (doublelistiterator.hasNext()) {
                double d0 = (Double) doublelistiterator.next();
                float f2 = (float) (d0 - collisionBox.minY);

                if (f2 >= 0.0F && f2 != stepHeight) {
                    if (f2 > f) {
                        break;
                    }

                    floatarrayset.add(f2);
                }
            }
        }

        float[] afloat = floatarrayset.toFloatArray();

        FloatArrays.unstableSort(afloat);
        return afloat;
    }

    public static Vec3 collideBoundingBox(@Nullable Entity entity, Vec3 movement, AABB entityBoundingBox, Level world, List<VoxelShape> collisions) {
        List<VoxelShape> list1 = Entity.collectColliders(entity, world, collisions, entityBoundingBox.expandTowards(movement));

        return Entity.collideWithShapes(movement, entityBoundingBox, list1);
    }

    private static List<VoxelShape> collectColliders(@Nullable Entity entity, Level world, List<VoxelShape> regularCollisions, AABB movingEntityBoundingBox) {
        Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(regularCollisions.size() + 1);

        if (!regularCollisions.isEmpty()) {
            builder.addAll(regularCollisions);
        }

        WorldBorder worldborder = world.getWorldBorder();
        boolean flag = entity != null && worldborder.isInsideCloseToBorder(entity, movingEntityBoundingBox);

        if (flag) {
            builder.add(worldborder.getCollisionShape());
        }

        builder.addAll(world.getBlockCollisions(entity, movingEntityBoundingBox));
        return builder.build();
    }

    private static Vec3 collideWithShapes(Vec3 movement, AABB entityBoundingBox, List<VoxelShape> collisions) {
        if (collisions.isEmpty()) {
            return movement;
        } else {
            double d0 = movement.x;
            double d1 = movement.y;
            double d2 = movement.z;

            if (d1 != 0.0D) {
                d1 = Shapes.collide(Direction.Axis.Y, entityBoundingBox, collisions, d1);
                if (d1 != 0.0D) {
                    entityBoundingBox = entityBoundingBox.move(0.0D, d1, 0.0D);
                }
            }

            boolean flag = Math.abs(d0) < Math.abs(d2);

            if (flag && d2 != 0.0D) {
                d2 = Shapes.collide(Direction.Axis.Z, entityBoundingBox, collisions, d2);
                if (d2 != 0.0D) {
                    entityBoundingBox = entityBoundingBox.move(0.0D, 0.0D, d2);
                }
            }

            if (d0 != 0.0D) {
                d0 = Shapes.collide(Direction.Axis.X, entityBoundingBox, collisions, d0);
                if (!flag && d0 != 0.0D) {
                    entityBoundingBox = entityBoundingBox.move(d0, 0.0D, 0.0D);
                }
            }

            if (!flag && d2 != 0.0D) {
                d2 = Shapes.collide(Direction.Axis.Z, entityBoundingBox, collisions, d2);
            }

            return new Vec3(d0, d1, d2);
        }
    }

    protected float nextStep() {
        return (float) ((int) this.moveDist + 1);
    }

    protected SoundEvent getSwimSound() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    // CraftBukkit start - Add delegate methods
    public SoundEvent getSwimSound0() {
        return this.getSwimSound();
    }

    public SoundEvent getSwimSplashSound0() {
        return this.getSwimSplashSound();
    }

    public SoundEvent getSwimHighSpeedSplashSound0() {
        return this.getSwimHighSpeedSplashSound();
    }
    // CraftBukkit end

    protected void checkInsideBlocks() {
        AABB axisalignedbb = this.getBoundingBox();
        BlockPos blockposition = BlockPos.containing(axisalignedbb.minX + 1.0E-7D, axisalignedbb.minY + 1.0E-7D, axisalignedbb.minZ + 1.0E-7D);
        BlockPos blockposition1 = BlockPos.containing(axisalignedbb.maxX - 1.0E-7D, axisalignedbb.maxY - 1.0E-7D, axisalignedbb.maxZ - 1.0E-7D);

        if (this.level().hasChunksAt(blockposition, blockposition1)) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int i = blockposition.getX(); i <= blockposition1.getX(); ++i) {
                for (int j = blockposition.getY(); j <= blockposition1.getY(); ++j) {
                    for (int k = blockposition.getZ(); k <= blockposition1.getZ(); ++k) {
                        if (!this.isAlive()) {
                            return;
                        }

                        blockposition_mutableblockposition.set(i, j, k);
                        BlockState iblockdata = this.level().getBlockState(blockposition_mutableblockposition);

                        try {
                            iblockdata.entityInside(this.level(), blockposition_mutableblockposition, this);
                            this.onInsideBlock(iblockdata);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Block being collided with");

                            CrashReportCategory.populateBlockDetails(crashreportsystemdetails, this.level(), blockposition_mutableblockposition, iblockdata);
                            throw new ReportedException(crashreport);
                        }
                    }
                }
            }
        }

    }

    protected void onInsideBlock(BlockState state) {}

    public BlockPos adjustSpawnLocation(ServerLevel world, BlockPos basePos) {
        BlockPos blockposition1 = world.getSharedSpawnPos();
        Vec3 vec3d = blockposition1.getCenter();
        int i = world.getChunkAt(blockposition1).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockposition1.getX(), blockposition1.getZ()) + 1;

        return BlockPos.containing(vec3d.x, (double) i, vec3d.z);
    }

    public void gameEvent(Holder<GameEvent> event, @Nullable Entity entity) {
        this.level().gameEvent(entity, event, this.position);
    }

    public void gameEvent(Holder<GameEvent> event) {
        this.gameEvent(event, this);
    }

    private void walkingStepSound(BlockPos pos, BlockState state) {
        this.playStepSound(pos, state);
        if (this.shouldPlayAmethystStepSound(state)) {
            this.playAmethystStepSound();
        }

    }

    protected void waterSwimSound() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.35F : 0.4F;
        Vec3 vec3d = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f);

        this.playSwimSound(f1);
    }

    protected BlockPos getPrimaryStepSoundBlockPos(BlockPos pos) {
        BlockPos blockposition1 = pos.above();
        BlockState iblockdata = this.level().getBlockState(blockposition1);

        return !iblockdata.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) && !iblockdata.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) ? pos : blockposition1;
    }

    protected void playCombinationStepSounds(BlockState primaryState, BlockState secondaryState) {
        SoundType soundeffecttype = primaryState.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
        this.playMuffledStepSound(secondaryState);
    }

    protected void playMuffledStepSound(BlockState state) {
        SoundType soundeffecttype = state.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.05F, soundeffecttype.getPitch() * 0.8F);
    }

    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundType soundeffecttype = state.getSoundType();

        this.playSound(soundeffecttype.getStepSound(), soundeffecttype.getVolume() * 0.15F, soundeffecttype.getPitch());
    }

    private boolean shouldPlayAmethystStepSound(BlockState state) {
        return state.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
    }

    private void playAmethystStepSound() {
        this.crystalSoundIntensity *= (float) Math.pow(0.997D, (double) (this.tickCount - this.lastCrystalSoundPlayTick));
        this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
        float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
        float f1 = 0.1F + this.crystalSoundIntensity * 1.2F;

        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, f1, f);
        this.lastCrystalSoundPlayTick = this.tickCount;
    }

    protected void playSwimSound(float volume) {
        this.playSound(this.getSwimSound(), volume, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {}

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) {
            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
        }

    }

    public void playSound(SoundEvent event) {
        if (!this.isSilent()) {
            this.playSound(event, 1.0F, 1.0F);
        }

    }

    public boolean isSilent() {
        return (Boolean) this.entityData.get(Entity.DATA_SILENT);
    }

    public void setSilent(boolean silent) {
        this.entityData.set(Entity.DATA_SILENT, silent);
    }

    public boolean isNoGravity() {
        return (Boolean) this.entityData.get(Entity.DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean noGravity) {
        this.entityData.set(Entity.DATA_NO_GRAVITY, noGravity);
    }

    protected double getDefaultGravity() {
        return 0.0D;
    }

    public final double getGravity() {
        return this.isNoGravity() ? 0.0D : this.getDefaultGravity();
    }

    protected void applyGravity() {
        double d0 = this.getGravity();

        if (d0 != 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -d0, 0.0D));
        }

    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
        if (onGround) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().fallOn(this.level(), state, landedPosition, this, this.fallDistance);
                this.level().gameEvent((Holder) GameEvent.HIT_GROUND, this.position, GameEvent.Context.of(this, (BlockState) this.mainSupportingBlockPos.map((blockposition1) -> {
                    return this.level().getBlockState(blockposition1);
                }).orElse(state)));
            }

            this.resetFallDistance();
        } else if (heightDifference < 0.0D) {
            this.fallDistance -= (float) heightDifference;
        }

    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (this.type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        } else {
            if (this.isVehicle()) {
                Iterator iterator = this.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();

                    entity.causeFallDamage(fallDistance, damageMultiplier, damageSource);
                }
            }

            return false;
        }
    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    public boolean isInRain() {
        BlockPos blockposition = this.blockPosition();

        return this.level().isRainingAt(blockposition) || this.level().isRainingAt(BlockPos.containing((double) blockposition.getX(), this.getBoundingBox().maxY, (double) blockposition.getZ()));
    }

    public boolean isInBubbleColumn() {
        return this.getInBlockState().is(Blocks.BUBBLE_COLUMN);
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInWaterRainOrBubble() {
        return this.isInWater() || this.isInRain() || this.isInBubbleColumn();
    }

    public boolean isInWaterOrBubble() {
        return this.isInWater() || this.isInBubbleColumn();
    }

    public boolean isInLiquid() {
        return this.isInWaterOrBubble() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(FluidTags.WATER));
        }

    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d0 = this.level().dimensionType().ultraWarm() ? 0.007D : 0.0023333333333333335D;
        boolean flag = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d0);

        return this.isInWater() || flag;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        Entity entity = this.getVehicle();

        if (entity instanceof Boat entityboat) {
            if (!entityboat.isUnderWater()) {
                this.wasTouchingWater = false;
                return;
            }
        }

        if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014D)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
            this.clearFire();
        } else {
            this.wasTouchingWater = false;
        }

    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.fluidOnEyes.clear();
        double d0 = this.getEyeY();
        Entity entity = this.getVehicle();

        if (entity instanceof Boat entityboat) {
            if (!entityboat.isUnderWater() && entityboat.getBoundingBox().maxY >= d0 && entityboat.getBoundingBox().minY <= d0) {
                return;
            }
        }

        BlockPos blockposition = BlockPos.containing(this.getX(), d0, this.getZ());
        FluidState fluid = this.level().getFluidState(blockposition);
        double d1 = (double) ((float) blockposition.getY() + fluid.getHeight(this.level(), blockposition));

        if (d1 > d0) {
            Stream stream = fluid.getTags();
            Set set = this.fluidOnEyes;

            Objects.requireNonNull(this.fluidOnEyes);
            stream.forEach(set::add);
        }

    }

    protected void doWaterSplashEffect() {
        Entity entity = (Entity) Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 vec3d = entity.getDeltaMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f);

        if (f1 < 0.25F) {
            this.playSound(this.getSwimSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), f1, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float f2 = (float) Mth.floor(this.getY());

        double d0;
        double d1;
        int i;

        for (i = 0; (float) i < 1.0F + this.dimensions.width() * 20.0F; ++i) {
            d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y - this.random.nextDouble() * 0.20000000298023224D, vec3d.z);
        }

        for (i = 0; (float) i < 1.0F + this.dimensions.width() * 20.0F; ++i) {
            d0 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            d1 = (this.random.nextDouble() * 2.0D - 1.0D) * (double) this.dimensions.width();
            this.level().addParticle(ParticleTypes.SPLASH, this.getX() + d0, (double) (f2 + 1.0F), this.getZ() + d1, vec3d.x, vec3d.y, vec3d.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    /** @deprecated */
    @Deprecated
    protected BlockState getBlockStateOnLegacy() {
        return this.level().getBlockState(this.getOnPosLegacy());
    }

    public BlockState getBlockStateOn() {
        return this.level().getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        BlockPos blockposition = this.getOnPosLegacy();
        BlockState iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 vec3d = this.getDeltaMovement();
            BlockPos blockposition1 = this.blockPosition();
            double d0 = this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width();
            double d1 = this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.dimensions.width();

            if (blockposition1.getX() != blockposition.getX()) {
                d0 = Mth.clamp(d0, (double) blockposition.getX(), (double) blockposition.getX() + 1.0D);
            }

            if (blockposition1.getZ() != blockposition.getZ()) {
                d1 = Mth.clamp(d1, (double) blockposition.getZ(), (double) blockposition.getZ() + 1.0D);
            }

            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), d0, this.getY() + 0.1D, d1, vec3d.x * -4.0D, 1.5D, vec3d.z * -4.0D);
        }

    }

    public boolean isEyeInFluid(TagKey<Fluid> fluidTag) {
        return this.fluidOnEyes.contains(fluidTag);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0D;
    }

    public void moveRelative(float speed, Vec3 movementInput) {
        Vec3 vec3d1 = Entity.getInputVector(movementInput, speed, this.getYRot());

        this.setDeltaMovement(this.getDeltaMovement().add(vec3d1));
    }

    private static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
        double d0 = movementInput.lengthSqr();

        if (d0 < 1.0E-7D) {
            return Vec3.ZERO;
        } else {
            Vec3 vec3d1 = (d0 > 1.0D ? movementInput.normalize() : movementInput).scale((double) speed);
            float f2 = Mth.sin(yaw * 0.017453292F);
            float f3 = Mth.cos(yaw * 0.017453292F);

            return new Vec3(vec3d1.x * (double) f3 - vec3d1.z * (double) f2, vec3d1.y, vec3d1.z * (double) f3 + vec3d1.x * (double) f2);
        }
    }

    /** @deprecated */
    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ()) ? this.level().getLightLevelDependentMagicValue(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())) : 0.0F;
    }

    public void absMoveTo(double x, double y, double z, float yaw, float pitch) {
        this.absMoveTo(x, y, z);
        this.absRotateTo(yaw, pitch);
    }

    public void absRotateTo(float yaw, float pitch) {
        this.setYRot(yaw % 360.0F);
        this.setXRot(Mth.clamp(pitch, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void absMoveTo(double x, double y, double z) {
        double d3 = Mth.clamp(x, -3.0E7D, 3.0E7D);
        double d4 = Mth.clamp(z, -3.0E7D, 3.0E7D);

        this.xo = d3;
        this.yo = y;
        this.zo = d4;
        this.setPos(d3, y, d4);
        if (this.valid) this.level.getChunk((int) Math.floor(this.getX()) >> 4, (int) Math.floor(this.getZ()) >> 4); // CraftBukkit
    }

    public void moveTo(Vec3 pos) {
        this.moveTo(pos.x, pos.y, pos.z);
    }

    public void moveTo(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
    }

    public void moveTo(BlockPos pos, float yaw, float pitch) {
        this.moveTo(pos.getBottomCenter(), yaw, pitch);
    }

    public void moveTo(Vec3 pos, float yaw, float pitch) {
        this.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
    }

    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        // Paper start - Fix Entity Teleportation and cancel velocity if teleported
        if (!preserveMotion) {
            this.deltaMovement = Vec3.ZERO;
        } else {
            this.preserveMotion = false;
        }
        // Paper end - Fix Entity Teleportation and cancel velocity if teleported
        this.setPosRaw(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setOldPosAndRot();
        this.reapplyPosition();
    }

    public final void setOldPosAndRot() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
        this.xOld = d0;
        this.yOld = d1;
        this.zOld = d2;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public float distanceTo(Entity entity) {
        float f = (float) (this.getX() - entity.getX());
        float f1 = (float) (this.getY() - entity.getY());
        float f2 = (float) (this.getZ() - entity.getZ());

        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public double distanceToSqr(double x, double y, double z) {
        double d3 = this.getX() - x;
        double d4 = this.getY() - y;
        double d5 = this.getZ() - z;

        return d3 * d3 + d4 * d4 + d5 * d5;
    }

    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3 vector) {
        double d0 = this.getX() - vector.x;
        double d1 = this.getY() - vector.y;
        double d2 = this.getZ() - vector.z;

        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public void playerTouch(Player player) {}

    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (this.level.paperConfig().collisions.onlyPlayersCollide && !(entity instanceof ServerPlayer || this instanceof ServerPlayer)) return; // Paper - Collision option for requiring a player participant
                double d0 = entity.getX() - this.getX();
                double d1 = entity.getZ() - this.getZ();
                double d2 = Mth.absMax(d0, d1);

                if (d2 >= 0.009999999776482582D) {
                    d2 = Math.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;

                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= 0.05000000074505806D;
                    d1 *= 0.05000000074505806D;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d0, 0.0D, -d1);
                    }

                    if (!entity.isVehicle() && entity.isPushable()) {
                        entity.push(d0, 0.0D, d1);
                    }
                }

            }
        }
    }

    public void push(Vec3 velocity) {
        this.push(velocity.x, velocity.y, velocity.z);
    }

    public void push(double deltaX, double deltaY, double deltaZ) {
        // Paper start - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
        this.push(deltaX, deltaY, deltaZ, null);
    }

    public void push(double deltaX, double deltaY, double deltaZ, @org.jetbrains.annotations.Nullable Entity pushingEntity) {
        org.bukkit.util.Vector delta = new org.bukkit.util.Vector(deltaX, deltaY, deltaZ);
        if (pushingEntity != null) {
            io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event = new io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent(this.getBukkitEntity(), io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.PUSH, pushingEntity.getBukkitEntity(), delta);
            if (!event.callEvent()) {
                return;
            }
            delta = event.getKnockback();
        }
        this.setDeltaMovement(this.getDeltaMovement().add(delta.getX(), delta.getY(), delta.getZ()));
        // Paper end - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
        this.hasImpulse = true;
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            this.markHurt();
            return false;
        }
    }

    public final Vec3 getViewVector(float tickDelta) {
        return this.calculateViewVector(this.getViewXRot(tickDelta), this.getViewYRot(tickDelta));
    }

    public Direction getNearestViewDirection() {
        return Direction.getNearest(this.getViewVector(1.0F));
    }

    public float getViewXRot(float tickDelta) {
        return tickDelta == 1.0F ? this.getXRot() : Mth.lerp(tickDelta, this.xRotO, this.getXRot());
    }

    public float getViewYRot(float tickDelta) {
        return tickDelta == 1.0F ? this.getYRot() : Mth.lerp(tickDelta, this.yRotO, this.getYRot());
    }

    public final Vec3 calculateViewVector(float pitch, float yaw) {
        float f2 = pitch * 0.017453292F;
        float f3 = -yaw * 0.017453292F;
        float f4 = Mth.cos(f3);
        float f5 = Mth.sin(f3);
        float f6 = Mth.cos(f2);
        float f7 = Mth.sin(f2);

        return new Vec3((double) (f5 * f6), (double) (-f7), (double) (f4 * f6));
    }

    public final Vec3 getUpVector(float tickDelta) {
        return this.calculateUpVector(this.getViewXRot(tickDelta), this.getViewYRot(tickDelta));
    }

    protected final Vec3 calculateUpVector(float pitch, float yaw) {
        return this.calculateViewVector(pitch - 90.0F, yaw);
    }

    public final Vec3 getEyePosition() {
        return new Vec3(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3 getEyePosition(float tickDelta) {
        double d0 = Mth.lerp((double) tickDelta, this.xo, this.getX());
        double d1 = Mth.lerp((double) tickDelta, this.yo, this.getY()) + (double) this.getEyeHeight();
        double d2 = Mth.lerp((double) tickDelta, this.zo, this.getZ());

        return new Vec3(d0, d1, d2);
    }

    public Vec3 getLightProbePosition(float tickDelta) {
        return this.getEyePosition(tickDelta);
    }

    public final Vec3 getPosition(float delta) {
        double d0 = Mth.lerp((double) delta, this.xo, this.getX());
        double d1 = Mth.lerp((double) delta, this.yo, this.getY());
        double d2 = Mth.lerp((double) delta, this.zo, this.getZ());

        return new Vec3(d0, d1, d2);
    }

    public HitResult pick(double maxDistance, float tickDelta, boolean includeFluids) {
        Vec3 vec3d = this.getEyePosition(tickDelta);
        Vec3 vec3d1 = this.getViewVector(tickDelta);
        Vec3 vec3d2 = vec3d.add(vec3d1.x * maxDistance, vec3d1.y * maxDistance, vec3d1.z * maxDistance);

        return this.level().clip(new ClipContext(vec3d, vec3d2, ClipContext.Block.OUTLINE, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
    }

    public boolean canBeHitByProjectile() {
        return this.isAlive() && this.isPickable();
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        // Paper start - Climbing should not bypass cramming gamerule
        return isCollidable(false);
    }

    public boolean isCollidable(boolean ignoreClimbing) {
        // Paper end - Climbing should not bypass cramming gamerule
        return false;
    }

    // CraftBukkit start - collidable API
    public boolean canCollideWithBukkit(Entity entity) {
        return this.isPushable();
    }
    // CraftBukkit end

    public void awardKillScore(Entity entityKilled, int score, DamageSource damageSource) {
        if (entityKilled instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer) entityKilled, this, damageSource);
        }

    }

    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        double d3 = this.getX() - cameraX;
        double d4 = this.getY() - cameraY;
        double d5 = this.getZ() - cameraZ;
        double d6 = d3 * d3 + d4 * d4 + d5 * d5;

        return this.shouldRenderAtSqrDistance(d6);
    }

    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize();

        if (Double.isNaN(d1)) {
            d1 = 1.0D;
        }

        d1 *= 64.0D * Entity.viewScale;
        return distance < d1 * d1;
    }

    public boolean saveAsPassenger(CompoundTag nbt) {
        // CraftBukkit start - allow excluding certain data when saving
        return this.saveAsPassenger(nbt, true);
    }

    public boolean saveAsPassenger(CompoundTag nbttagcompound, boolean includeAll) {
        // CraftBukkit end
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else {
            String s = this.getEncodeId();

            if (!this.persist || s == null) { // CraftBukkit - persist flag
                return false;
            } else {
                nbttagcompound.putString("id", s);
                this.saveWithoutId(nbttagcompound, includeAll); // CraftBukkit - pass on includeAll
                return true;
            }
        }
    }

    public boolean save(CompoundTag nbt) {
        return this.isPassenger() ? false : this.saveAsPassenger(nbt);
    }

    public CompoundTag saveWithoutId(CompoundTag nbt) {
        // CraftBukkit start - allow excluding certain data when saving
        return this.saveWithoutId(nbt, true);
    }

    public CompoundTag saveWithoutId(CompoundTag nbttagcompound, boolean includeAll) {
        // CraftBukkit end
        try {
            // CraftBukkit start - selectively save position
            if (includeAll) {
                if (this.vehicle != null) {
                    nbttagcompound.put("Pos", this.newDoubleList(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
                } else {
                    nbttagcompound.put("Pos", this.newDoubleList(this.getX(), this.getY(), this.getZ()));
                }
            }
            // CraftBukkit end

            Vec3 vec3d = this.getDeltaMovement();

            nbttagcompound.put("Motion", this.newDoubleList(vec3d.x, vec3d.y, vec3d.z));

            // CraftBukkit start - Checking for NaN pitch/yaw and resetting to zero
            // TODO: make sure this is the best way to address this.
            if (Float.isNaN(this.yRot)) {
                this.yRot = 0;
            }

            if (Float.isNaN(this.xRot)) {
                this.xRot = 0;
            }
            // CraftBukkit end

            nbttagcompound.put("Rotation", this.newFloatList(this.getYRot(), this.getXRot()));
            nbttagcompound.putFloat("FallDistance", this.fallDistance);
            nbttagcompound.putShort("Fire", (short) this.remainingFireTicks);
            nbttagcompound.putShort("Air", (short) this.getAirSupply());
            nbttagcompound.putBoolean("OnGround", this.onGround());
            nbttagcompound.putBoolean("Invulnerable", this.invulnerable);
            nbttagcompound.putInt("PortalCooldown", this.portalCooldown);
            // CraftBukkit start - selectively save uuid and world
            if (includeAll) {
                nbttagcompound.putUUID("UUID", this.getUUID());
                // PAIL: Check above UUID reads 1.8 properly, ie: UUIDMost / UUIDLeast
                nbttagcompound.putLong("WorldUUIDLeast", ((ServerLevel) this.level).getWorld().getUID().getLeastSignificantBits());
                nbttagcompound.putLong("WorldUUIDMost", ((ServerLevel) this.level).getWorld().getUID().getMostSignificantBits());
            }
            nbttagcompound.putInt("Bukkit.updateLevel", Entity.CURRENT_LEVEL);
            if (!this.persist) {
                nbttagcompound.putBoolean("Bukkit.persist", this.persist);
            }
            if (!this.visibleByDefault) {
                nbttagcompound.putBoolean("Bukkit.visibleByDefault", this.visibleByDefault);
            }
            if (this.persistentInvisibility) {
                nbttagcompound.putBoolean("Bukkit.invisible", this.persistentInvisibility);
            }
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (this.maxAirTicks != this.getDefaultMaxAirSupply()) {
                nbttagcompound.putInt("Bukkit.MaxAirSupply", this.getMaxAirSupply());
            }
            nbttagcompound.putInt("Spigot.ticksLived", this.tickCount);
            // CraftBukkit end
            Component ichatbasecomponent = this.getCustomName();

            if (ichatbasecomponent != null) {
                nbttagcompound.putString("CustomName", Component.Serializer.toJson(ichatbasecomponent, this.registryAccess()));
            }

            if (this.isCustomNameVisible()) {
                nbttagcompound.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                nbttagcompound.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                nbttagcompound.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                nbttagcompound.putBoolean("Glowing", true);
            }

            int i = this.getTicksFrozen();

            if (i > 0) {
                nbttagcompound.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                nbttagcompound.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            ListTag nbttaglist;
            Iterator iterator;

            if (!this.tags.isEmpty()) {
                nbttaglist = new ListTag();
                iterator = this.tags.iterator();

                while (iterator.hasNext()) {
                    String s = (String) iterator.next();

                    nbttaglist.add(StringTag.valueOf(s));
                }

                nbttagcompound.put("Tags", nbttaglist);
            }

            this.addAdditionalSaveData(nbttagcompound, includeAll); // CraftBukkit - pass on includeAll
            if (this.isVehicle()) {
                nbttaglist = new ListTag();
                iterator = this.getPassengers().iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();
                    CompoundTag nbttagcompound1 = new CompoundTag();

                    if (entity.saveAsPassenger(nbttagcompound1, includeAll)) { // CraftBukkit - pass on includeAll
                        nbttaglist.add(nbttagcompound1);
                    }
                }

                if (!nbttaglist.isEmpty()) {
                    nbttagcompound.put("Passengers", nbttaglist);
                }
            }

            // CraftBukkit start - stores eventually existing bukkit values
            if (this.bukkitEntity != null) {
                this.bukkitEntity.storeBukkitValues(nbttagcompound);
            }
            // CraftBukkit end
            // Paper start
            if (this.origin != null) {
                UUID originWorld = this.originWorld != null ? this.originWorld : this.level != null ? this.level.getWorld().getUID() : null;
                if (originWorld != null) {
                    nbttagcompound.putUUID("Paper.OriginWorld", originWorld);
                }
                nbttagcompound.put("Paper.Origin", this.newDoubleList(origin.getX(), origin.getY(), origin.getZ()));
            }
            if (spawnReason != null) {
                nbttagcompound.putString("Paper.SpawnReason", spawnReason.name());
            }
            // Save entity's from mob spawner status
            if (spawnedViaMobSpawner) {
                nbttagcompound.putBoolean("Paper.FromMobSpawner", true);
            }
            if (fromNetherPortal) {
                nbttagcompound.putBoolean("Paper.FromNetherPortal", true);
            }
            // Paper end
            return nbttagcompound;
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being saved");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    public void load(CompoundTag nbt) {
        try {
            ListTag nbttaglist = nbt.getList("Pos", 6);
            ListTag nbttaglist1 = nbt.getList("Motion", 6);
            ListTag nbttaglist2 = nbt.getList("Rotation", 5);
            double d0 = nbttaglist1.getDouble(0);
            double d1 = nbttaglist1.getDouble(1);
            double d2 = nbttaglist1.getDouble(2);

            this.setDeltaMovement(Math.abs(d0) > 10.0D ? 0.0D : d0, Math.abs(d1) > 10.0D ? 0.0D : d1, Math.abs(d2) > 10.0D ? 0.0D : d2);
            double d3 = 3.0000512E7D;

            this.setPosRaw(Mth.clamp(nbttaglist.getDouble(0), -3.0000512E7D, 3.0000512E7D), Mth.clamp(nbttaglist.getDouble(1), -2.0E7D, 2.0E7D), Mth.clamp(nbttaglist.getDouble(2), -3.0000512E7D, 3.0000512E7D));
            this.setYRot(nbttaglist2.getFloat(0));
            this.setXRot(nbttaglist2.getFloat(1));
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = nbt.getFloat("FallDistance");
            this.remainingFireTicks = nbt.getShort("Fire");
            if (nbt.contains("Air")) {
                this.setAirSupply(nbt.getShort("Air"));
            }

            this.onGround = nbt.getBoolean("OnGround");
            this.invulnerable = nbt.getBoolean("Invulnerable");
            this.portalCooldown = nbt.getInt("PortalCooldown");
            if (nbt.hasUUID("UUID")) {
                this.uuid = nbt.getUUID("UUID");
                this.stringUUID = this.uuid.toString();
            }

            if (Double.isFinite(this.getX()) && Double.isFinite(this.getY()) && Double.isFinite(this.getZ())) {
                if (Double.isFinite((double) this.getYRot()) && Double.isFinite((double) this.getXRot())) {
                    this.reapplyPosition();
                    this.setRot(this.getYRot(), this.getXRot());
                    if (nbt.contains("CustomName", 8)) {
                        String s = nbt.getString("CustomName");

                        try {
                            this.setCustomName(Component.Serializer.fromJson(s, this.registryAccess()));
                        } catch (Exception exception) {
                            Entity.LOGGER.warn("Failed to parse entity custom name {}", s, exception);
                        }
                    }

                    this.setCustomNameVisible(nbt.getBoolean("CustomNameVisible"));
                    this.setSilent(nbt.getBoolean("Silent"));
                    this.setNoGravity(nbt.getBoolean("NoGravity"));
                    this.setGlowingTag(nbt.getBoolean("Glowing"));
                    this.setTicksFrozen(nbt.getInt("TicksFrozen"));
                    this.hasVisualFire = nbt.getBoolean("HasVisualFire");
                    if (nbt.contains("Tags", 9)) {
                        this.tags.clear();
                        ListTag nbttaglist3 = nbt.getList("Tags", 8);
                        int i = Math.min(nbttaglist3.size(), 1024);

                        for (int j = 0; j < i; ++j) {
                            this.tags.add(nbttaglist3.getString(j));
                        }
                    }

                    this.readAdditionalSaveData(nbt);
                    if (this.repositionEntityAfterLoad()) {
                        this.reapplyPosition();
                    }

                } else {
                    throw new IllegalStateException("Entity has invalid rotation");
                }
            } else {
                throw new IllegalStateException("Entity has invalid position");
            }

            // CraftBukkit start
            // Spigot start
            if (this instanceof net.minecraft.world.entity.LivingEntity) {
                this.tickCount = nbt.getInt("Spigot.ticksLived");
            }
            // Spigot end
            this.persist = !nbt.contains("Bukkit.persist") || nbt.getBoolean("Bukkit.persist");
            this.visibleByDefault = !nbt.contains("Bukkit.visibleByDefault") || nbt.getBoolean("Bukkit.visibleByDefault");
            // SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
            if (nbt.contains("Bukkit.MaxAirSupply")) {
                this.maxAirTicks = nbt.getInt("Bukkit.MaxAirSupply");
            }
            // CraftBukkit end

            // CraftBukkit start
            // Paper - move world parsing/loading to PlayerList#placeNewPlayer
            this.getBukkitEntity().readBukkitValues(nbt);
            if (nbt.contains("Bukkit.invisible")) {
                boolean bukkitInvisible = nbt.getBoolean("Bukkit.invisible");
                this.setInvisible(bukkitInvisible);
                this.persistentInvisibility = bukkitInvisible;
            }
            // CraftBukkit end

            // Paper start
            ListTag originTag = nbt.getList("Paper.Origin", net.minecraft.nbt.Tag.TAG_DOUBLE);
            if (!originTag.isEmpty()) {
                UUID originWorld = null;
                if (nbt.contains("Paper.OriginWorld")) {
                    originWorld = nbt.getUUID("Paper.OriginWorld");
                } else if (this.level != null) {
                    originWorld = this.level.getWorld().getUID();
                }
                this.originWorld = originWorld;
                origin = new org.bukkit.util.Vector(originTag.getDouble(0), originTag.getDouble(1), originTag.getDouble(2));
            }

            spawnedViaMobSpawner = nbt.getBoolean("Paper.FromMobSpawner"); // Restore entity's from mob spawner status
            fromNetherPortal = nbt.getBoolean("Paper.FromNetherPortal");
            if (nbt.contains("Paper.SpawnReason")) {
                String spawnReasonName = nbt.getString("Paper.SpawnReason");
                try {
                    spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(spawnReasonName);
                } catch (Exception ignored) {
                    LOGGER.error("Unknown SpawnReason " + spawnReasonName + " for " + this);
                }
            }
            if (spawnReason == null) {
                if (spawnedViaMobSpawner) {
                    spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER;
                } else if (this instanceof Mob && (this instanceof net.minecraft.world.entity.animal.Animal || this instanceof net.minecraft.world.entity.animal.AbstractFish) && !((Mob) this).removeWhenFarAway(0.0)) {
                    if (!nbt.getBoolean("PersistenceRequired")) {
                        spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL;
                    }
                }
            }
            if (spawnReason == null) {
                spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
            }
            // Paper end

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Entity being loaded");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    public final String getEncodeId() {
        EntityType<?> entitytypes = this.getType();
        ResourceLocation minecraftkey = EntityType.getKey(entitytypes);

        return entitytypes.canSerialize() && minecraftkey != null ? minecraftkey.toString() : null;
    }

    // CraftBukkit start - allow excluding certain data when saving
    protected void addAdditionalSaveData(CompoundTag nbttagcompound, boolean includeAll) {
        this.addAdditionalSaveData(nbttagcompound);
    }
    // CraftBukkit end

    protected abstract void readAdditionalSaveData(CompoundTag nbt);

    protected abstract void addAdditionalSaveData(CompoundTag nbt);

    protected ListTag newDoubleList(double... values) {
        ListTag nbttaglist = new ListTag();
        double[] adouble1 = values;
        int i = values.length;

        for (int j = 0; j < i; ++j) {
            double d0 = adouble1[j];

            nbttaglist.add(DoubleTag.valueOf(d0));
        }

        return nbttaglist;
    }

    protected ListTag newFloatList(float... values) {
        ListTag nbttaglist = new ListTag();
        float[] afloat1 = values;
        int i = values.length;

        for (int j = 0; j < i; ++j) {
            float f = afloat1[j];

            nbttaglist.add(FloatTag.valueOf(f));
        }

        return nbttaglist;
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike item) {
        return this.spawnAtLocation(item, 0);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike item, int yOffset) {
        return this.spawnAtLocation(new ItemStack(item), (float) yOffset);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack stack) {
        return this.spawnAtLocation(stack, 0.0F);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack stack, float yOffset) {
        if (stack.isEmpty()) {
            return null;
        } else if (this.level().isClientSide) {
            return null;
        } else {
            // CraftBukkit start - Capture drops for death event
            if (this instanceof net.minecraft.world.entity.LivingEntity && !((net.minecraft.world.entity.LivingEntity) this).forceDrops) {
                ((net.minecraft.world.entity.LivingEntity) this).drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack)); // Paper - mirror so we can destroy it later
                return null;
            }
            // CraftBukkit end
            ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), this.getY() + (double) yOffset, this.getZ(), stack.copy()); // Paper - copy so we can destroy original
            stack.setCount(0); // Paper - destroy this item - if this ever leaks due to game bugs, ensure it doesn't dupe

            entityitem.setDefaultPickUpDelay();
            // CraftBukkit start
            EntityDropItemEvent event = new EntityDropItemEvent(this.getBukkitEntity(), (org.bukkit.entity.Item) entityitem.getBukkitEntity());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }
            // CraftBukkit end
            this.level().addFreshEntity(entityitem);
            return entityitem;
        }
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        } else {
            float f = this.dimensions.width() * 0.8F;
            AABB axisalignedbb = AABB.ofSize(this.getEyePosition(), (double) f, 1.0E-6D, (double) f);

            return BlockPos.betweenClosedStream(axisalignedbb).anyMatch((blockposition) -> {
                BlockState iblockdata = this.level().getBlockState(blockposition);

                return !iblockdata.isAir() && iblockdata.isSuffocating(this.level(), blockposition) && Shapes.joinIsNotEmpty(iblockdata.getCollisionShape(this.level(), blockposition).move((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ()), Shapes.create(axisalignedbb), BooleanOp.AND);
            });
        }
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.isAlive() && this instanceof Leashable leashable) {
            if (leashable.getLeashHolder() == player) {
                if (!this.level().isClientSide()) {
                    // CraftBukkit start - fire PlayerUnleashEntityEvent
                    // Paper start - Expand EntityUnleashEvent
                    org.bukkit.event.player.PlayerUnleashEntityEvent event = CraftEventFactory.callPlayerUnleashEntityEvent(this, player, hand, !player.hasInfiniteMaterials());
                    if (event.isCancelled()) {
                        // Paper end - Expand EntityUnleashEvent
                        ((ServerPlayer) player).connection.send(new ClientboundSetEntityLinkPacket(this, leashable.getLeashHolder()));
                        return InteractionResult.PASS;
                    }
                    // CraftBukkit end
                    leashable.dropLeash(true, event.isDropLeash()); // Paper - Expand EntityUnleashEvent
                    this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                }

                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            ItemStack itemstack = player.getItemInHand(hand);

            if (itemstack.is(Items.LEAD) && leashable.canHaveALeashAttachedToIt()) {
                if (!this.level().isClientSide()) {
                    // CraftBukkit start - fire PlayerLeashEntityEvent
                    if (CraftEventFactory.callPlayerLeashEntityEvent(this, player, player, hand).isCancelled()) {
                        ((ServerPlayer) player).resendItemInHands(); // SPIGOT-7615: Resend to fix client desync with used item
                        ((ServerPlayer) player).connection.send(new ClientboundSetEntityLinkPacket(this, leashable.getLeashHolder()));
                        return InteractionResult.PASS;
                    }
                    // CraftBukkit end
                    leashable.setLeashedTo(player, true);
                }

                itemstack.shrink(1);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    public boolean canCollideWith(Entity other) {
        return other.canBeCollidedWith() && !this.isPassengerOfSameVehicle(other);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public final void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            this.positionRider(passenger, Entity::setPos);
        }
    }

    protected void positionRider(Entity passenger, Entity.MoveFunction positionUpdater) {
        Vec3 vec3d = this.getPassengerRidingPosition(passenger);
        Vec3 vec3d1 = passenger.getVehicleAttachmentPoint(this);

        positionUpdater.accept(passenger, vec3d.x - vec3d1.x, vec3d.y - vec3d1.y, vec3d.z - vec3d1.z);
    }

    public void onPassengerTurned(Entity passenger) {}

    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        return this.getAttachments().get(EntityAttachment.VEHICLE, 0, this.yRot);
    }

    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return this.position().add(this.getPassengerAttachmentPoint(passenger, this.dimensions, 1.0F));
    }

    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        return Entity.getDefaultPassengerAttachmentPoint(this, passenger, dimensions.attachments());
    }

    protected static Vec3 getDefaultPassengerAttachmentPoint(Entity vehicle, Entity passenger, EntityAttachments attachments) {
        int i = vehicle.getPassengers().indexOf(passenger);

        return attachments.getClamped(EntityAttachment.PASSENGER, i, vehicle.yRot);
    }

    public boolean startRiding(Entity entity) {
        return this.startRiding(entity, false);
    }

    public boolean showVehicleHealth() {
        return this instanceof net.minecraft.world.entity.LivingEntity;
    }

    public boolean startRiding(Entity entity, boolean force) {
        if (entity == this.vehicle) {
            return false;
        } else if (!entity.couldAcceptPassenger()) {
            return false;
        } else {
            for (Entity entity1 = entity; entity1.vehicle != null; entity1 = entity1.vehicle) {
                if (entity1.vehicle == this) {
                    return false;
                }
            }

            if (!force && (!this.canRide(entity) || !entity.canAddPassenger(this))) {
                return false;
            } else {
                // CraftBukkit start
                if (entity.getBukkitEntity() instanceof Vehicle && this.getBukkitEntity() instanceof LivingEntity) {
                    VehicleEnterEvent event = new VehicleEnterEvent((Vehicle) entity.getBukkitEntity(), this.getBukkitEntity());
                    // Suppress during worldgen
                    if (this.valid) {
                        Bukkit.getPluginManager().callEvent(event);
                    }
                    if (event.isCancelled()) {
                        return false;
                    }
                }

                EntityMountEvent event = new EntityMountEvent(this.getBukkitEntity(), entity.getBukkitEntity());
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                if (event.isCancelled()) {
                    return false;
                }
                // CraftBukkit end
                if (this.isPassenger()) {
                    this.stopRiding();
                }

                this.setPose(net.minecraft.world.entity.Pose.STANDING);
                this.vehicle = entity;
                this.vehicle.addPassenger(this);
                entity.getIndirectPassengersStream().filter((entity2) -> {
                    return entity2 instanceof ServerPlayer;
                }).forEach((entity2) -> {
                    CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer) entity2);
                });
                return true;
            }
        }
    }

    protected boolean canRide(Entity entity) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; --i) {
            ((Entity) this.passengers.get(i)).stopRiding();
        }

    }

    public void removeVehicle() {
        // Paper start - Force entity dismount during teleportation
        this.removeVehicle(false);
    }
    public void removeVehicle(boolean suppressCancellation) {
        // Paper end - Force entity dismount during teleportation
        if (this.vehicle != null) {
            Entity entity = this.vehicle;

            this.vehicle = null;
            if (!entity.removePassenger(this, suppressCancellation)) this.vehicle = entity; // CraftBukkit // Paper - Force entity dismount during teleportation
        }

    }

    public void stopRiding() {
        // Paper start - Force entity dismount during teleportation
        this.stopRiding(false);
    }

    public void stopRiding(boolean suppressCancellation) {
        this.removeVehicle(suppressCancellation);
        // Paper end - Force entity dismount during teleportation
    }

    protected void addPassenger(Entity passenger) {
        if (passenger.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        } else {
            if (this.passengers.isEmpty()) {
                this.passengers = ImmutableList.of(passenger);
            } else {
                List<Entity> list = Lists.newArrayList(this.passengers);

                if (!this.level().isClientSide && passenger instanceof Player && !(this.getFirstPassenger() instanceof Player)) {
                    list.add(0, passenger);
                } else {
                    list.add(passenger);
                }

                this.passengers = ImmutableList.copyOf(list);
            }

            this.gameEvent(GameEvent.ENTITY_MOUNT, passenger);
        }
    }

    // Paper start - Force entity dismount during teleportation
    protected boolean removePassenger(Entity entity) { return removePassenger(entity, false);}
    protected boolean removePassenger(Entity entity, boolean suppressCancellation) { // CraftBukkit
        // Paper end - Force entity dismount during teleportation
        if (entity.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        } else {
            // CraftBukkit start
            CraftEntity craft = (CraftEntity) entity.getBukkitEntity().getVehicle();
            Entity orig = craft == null ? null : craft.getHandle();
            if (this.getBukkitEntity() instanceof Vehicle && entity.getBukkitEntity() instanceof LivingEntity) {
                VehicleExitEvent event = new VehicleExitEvent(
                        (Vehicle) this.getBukkitEntity(),
                        (LivingEntity) entity.getBukkitEntity(), !suppressCancellation // Paper - Force entity dismount during teleportation
                );
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                CraftEntity craftn = (CraftEntity) entity.getBukkitEntity().getVehicle();
                Entity n = craftn == null ? null : craftn.getHandle();
                if (event.isCancelled() || n != orig) {
                    return false;
                }
            }

            EntityDismountEvent event = new EntityDismountEvent(entity.getBukkitEntity(), this.getBukkitEntity(), !suppressCancellation); // Paper - Force entity dismount during teleportation
            // Suppress during worldgen
            if (this.valid) {
                Bukkit.getPluginManager().callEvent(event);
            }
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end
            if (this.passengers.size() == 1 && this.passengers.get(0) == entity) {
                this.passengers = ImmutableList.of();
            } else {
                this.passengers = (ImmutableList) this.passengers.stream().filter((entity1) -> {
                    return entity1 != entity;
                }).collect(ImmutableList.toImmutableList());
            }

            entity.boardingCooldown = 60;
            this.gameEvent(GameEvent.ENTITY_DISMOUNT, entity);
        }
        return true; // CraftBukkit
    }

    protected boolean canAddPassenger(Entity passenger) {
        return this.passengers.isEmpty();
    }

    protected boolean couldAcceptPassenger() {
        return true;
    }

    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    public double lerpTargetX() {
        return this.getX();
    }

    public double lerpTargetY() {
        return this.getY();
    }

    public double lerpTargetZ() {
        return this.getZ();
    }

    public float lerpTargetXRot() {
        return this.getXRot();
    }

    public float lerpTargetYRot() {
        return this.getYRot();
    }

    public void lerpHeadTo(float yaw, int interpolationSteps) {
        this.setYHeadRot(yaw);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3 getHandHoldingItemAngle(Item item) {
        if (!(this instanceof Player entityhuman)) {
            return Vec3.ZERO;
        } else {
            boolean flag = entityhuman.getOffhandItem().is(item) && !entityhuman.getMainHandItem().is(item);
            HumanoidArm enummainhand = flag ? entityhuman.getMainArm().getOpposite() : entityhuman.getMainArm();

            return this.calculateViewVector(0.0F, this.getYRot() + (float) (enummainhand == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5D);
        }
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.getXRot(), this.getYRot());
    }

    public Vec3 getForward() {
        return Vec3.directionFromRotation(this.getRotationVector());
    }

    public void setAsInsidePortal(Portal portal, BlockPos pos) {
        if (this.isOnPortalCooldown()) {
            this.setPortalCooldown();
        } else {
            if (this.portalProcess != null && this.portalProcess.isSamePortal(portal)) {
                this.portalProcess.updateEntryPosition(pos.immutable());
                this.portalProcess.setAsInsidePortalThisTick(true);
            } else {
                this.portalProcess = new PortalProcessor(portal, pos.immutable());
            }

        }
    }

    protected void handlePortal() {
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            this.processPortalCooldown();
            if (this.portalProcess != null) {
                if (this.portalProcess.processPortalTeleportation(worldserver, this, this.canUsePortal(false))) {
                    worldserver.getProfiler().push("portal");
                    this.setPortalCooldown();
                    DimensionTransition dimensiontransition = this.portalProcess.getPortalDestination(worldserver, this);

                    if (dimensiontransition != null) {
                        ServerLevel worldserver1 = dimensiontransition.newLevel();

                        if (this instanceof ServerPlayer || (worldserver1 != null && (worldserver1.dimension() == worldserver.dimension() || this.canChangeDimensions(worldserver, worldserver1)))) { // CraftBukkit - always call event for players
                            this.changeDimension(dimensiontransition);
                        }
                    }

                    worldserver.getProfiler().pop();
                } else if (this.portalProcess.hasExpired()) {
                    this.portalProcess = null;
                }

            }
        }
    }

    public int getDimensionChangingDelay() {
        Entity entity = this.getFirstPassenger();

        return entity instanceof ServerPlayer ? entity.getDimensionChangingDelay() : 300;
    }

    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    public void handleDamageEvent(DamageSource damageSource) {}

    public void handleEntityEvent(byte status) {
        switch (status) {
            case 53:
                HoneyBlock.showSlideParticles(this);
            default:
        }
    }

    public void animateHurt(float yaw) {}

    public boolean isOnFire() {
        boolean flag = this.level() != null && this.level().isClientSide;

        return !this.fireImmune() && (this.remainingFireTicks > 0 || flag && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean dismountsUnderwater() {
        return this.getType().is(EntityTypeTags.DISMOUNTS_UNDERWATER);
    }

    public boolean canControlVehicle() {
        return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER);
    }

    public void setShiftKeyDown(boolean sneaking) {
        this.setSharedFlag(1, sneaking);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(net.minecraft.world.entity.Pose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    public void setSprinting(boolean sprinting) {
        this.setSharedFlag(3, sprinting);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(net.minecraft.world.entity.Pose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean swimming) {
        // CraftBukkit start
        if (this.valid && this.isSwimming() != swimming && this instanceof net.minecraft.world.entity.LivingEntity) {
            if (CraftEventFactory.callToggleSwimEvent((net.minecraft.world.entity.LivingEntity) this, swimming).isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.setSharedFlag(4, swimming);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean glowing) {
        this.hasGlowingTag = glowing;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    public boolean isInvisibleTo(Player player) {
        if (player.isSpectator()) {
            return false;
        } else {
            PlayerTeam scoreboardteam = this.getTeam();

            return scoreboardteam != null && player != null && player.getTeam() == scoreboardteam && scoreboardteam.canSeeFriendlyInvisibles() ? false : this.isInvisible();
        }
    }

    public boolean isOnRails() {
        return false;
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> callback) {}

    @Nullable
    public PlayerTeam getTeam() {
        if (!this.level().paperConfig().scoreboards.allowNonPlayerEntitiesOnScoreboards && !(this instanceof Player)) { return null; } // Paper - Perf: Disable Scoreboards for non players by default
        return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    public boolean isAlliedTo(Entity other) {
        return this.isAlliedTo((Team) other.getTeam());
    }

    public boolean isAlliedTo(Team team) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(team) : false;
    }

    // CraftBukkit - start
    public void setInvisible(boolean invisible) {
        if (!this.persistentInvisibility) { // Prevent Minecraft from removing our invisibility flag
            this.setSharedFlag(5, invisible);
        }
        // CraftBukkit - end
    }

    public boolean getSharedFlag(int index) {
        return ((Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) & 1 << index) != 0;
    }

    public void setSharedFlag(int index, boolean value) {
        byte b0 = (Byte) this.entityData.get(Entity.DATA_SHARED_FLAGS_ID);

        if (value) {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 | 1 << index));
        } else {
            this.entityData.set(Entity.DATA_SHARED_FLAGS_ID, (byte) (b0 & ~(1 << index)));
        }

    }

    public int getMaxAirSupply() {
        return this.maxAirTicks; // CraftBukkit - SPIGOT-6907: re-implement LivingEntity#setMaximumAir()
    }

    public int getAirSupply() {
        return (Integer) this.entityData.get(Entity.DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int air) {
        // CraftBukkit start
        EntityAirChangeEvent event = new EntityAirChangeEvent(this.getBukkitEntity(), air);
        // Suppress during worldgen
        if (this.valid) {
            event.getEntity().getServer().getPluginManager().callEvent(event);
        }
        if (event.isCancelled() && this.getAirSupply() != air) {
            this.entityData.markDirty(Entity.DATA_AIR_SUPPLY_ID);
            return;
        }
        this.entityData.set(Entity.DATA_AIR_SUPPLY_ID, event.getAmount());
        // CraftBukkit end
    }

    public int getTicksFrozen() {
        return (Integer) this.entityData.get(Entity.DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int frozenTicks) {
        this.entityData.set(Entity.DATA_TICKS_FROZEN, frozenTicks);
    }

    public float getPercentFrozen() {
        int i = this.getTicksRequiredToFreeze();

        return (float) Math.min(this.getTicksFrozen(), i) / (float) i;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(ServerLevel world, LightningBolt lightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        // CraftBukkit start
        final org.bukkit.entity.Entity thisBukkitEntity = this.getBukkitEntity();
        final org.bukkit.entity.Entity stormBukkitEntity = lightning.getBukkitEntity();
        final PluginManager pluginManager = Bukkit.getPluginManager();
        // CraftBukkit end

        if (this.remainingFireTicks == 0) {
            // CraftBukkit start - Call a combust event when lightning strikes
            EntityCombustByEntityEvent entityCombustEvent = new EntityCombustByEntityEvent(stormBukkitEntity, thisBukkitEntity, 8.0F);
            pluginManager.callEvent(entityCombustEvent);
            if (!entityCombustEvent.isCancelled()) {
                this.igniteForSeconds(entityCombustEvent.getDuration(), false);
            }
            // CraftBukkit end
        }

        // CraftBukkit start
        if (thisBukkitEntity instanceof Hanging) {
            HangingBreakByEntityEvent hangingEvent = new HangingBreakByEntityEvent((Hanging) thisBukkitEntity, stormBukkitEntity);
            pluginManager.callEvent(hangingEvent);

            if (hangingEvent.isCancelled()) {
                return;
            }
        }

        if (this.fireImmune()) {
            return;
        }

        if (!this.hurt(this.damageSources().lightningBolt().customEntityDamager(lightning), 5.0F)) {
            return;
        }
        // CraftBukkit end
    }

    public void onAboveBubbleCol(boolean drag) {
        Vec3 vec3d = this.getDeltaMovement();
        double d0;

        if (drag) {
            d0 = Math.max(-0.9D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(1.8D, vec3d.y + 0.1D);
        }

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
    }

    public void onInsideBubbleColumn(boolean drag) {
        Vec3 vec3d = this.getDeltaMovement();
        double d0;

        if (drag) {
            d0 = Math.max(-0.3D, vec3d.y - 0.03D);
        } else {
            d0 = Math.min(0.7D, vec3d.y + 0.06D);
        }

        this.setDeltaMovement(vec3d.x, d0, vec3d.z);
        this.resetFallDistance();
    }

    public boolean killedEntity(ServerLevel world, net.minecraft.world.entity.LivingEntity other) {
        return true;
    }

    public void checkSlowFallDistance() {
        if (this.getDeltaMovement().y() > -0.5D && this.fallDistance > 1.0F) {
            this.fallDistance = 1.0F;
        }

    }

    public void resetFallDistance() {
        this.fallDistance = 0.0F;
    }

    protected void moveTowardsClosestSpace(double x, double y, double z) {
        BlockPos blockposition = BlockPos.containing(x, y, z);
        Vec3 vec3d = new Vec3(x - (double) blockposition.getX(), y - (double) blockposition.getY(), z - (double) blockposition.getZ());
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        Direction enumdirection = Direction.UP;
        double d3 = Double.MAX_VALUE;
        Direction[] aenumdirection = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection1 = aenumdirection[j];

            blockposition_mutableblockposition.setWithOffset(blockposition, enumdirection1);
            if (!this.level().getBlockState(blockposition_mutableblockposition).isCollisionShapeFullBlock(this.level(), blockposition_mutableblockposition)) {
                double d4 = vec3d.get(enumdirection1.getAxis());
                double d5 = enumdirection1.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D - d4 : d4;

                if (d5 < d3) {
                    d3 = d5;
                    enumdirection = enumdirection1;
                }
            }
        }

        float f = this.random.nextFloat() * 0.2F + 0.1F;
        float f1 = (float) enumdirection.getAxisDirection().getStep();
        Vec3 vec3d1 = this.getDeltaMovement().scale(0.75D);

        if (enumdirection.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement((double) (f1 * f), vec3d1.y, vec3d1.z);
        } else if (enumdirection.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec3d1.x, (double) (f1 * f), vec3d1.z);
        } else if (enumdirection.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec3d1.x, vec3d1.y, (double) (f1 * f));
        }

    }

    public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = multiplier;
    }

    private static Component removeAction(Component textComponent) {
        MutableComponent ichatmutablecomponent = textComponent.plainCopy().setStyle(textComponent.getStyle().withClickEvent((ClickEvent) null));
        Iterator iterator = textComponent.getSiblings().iterator();

        while (iterator.hasNext()) {
            Component ichatbasecomponent1 = (Component) iterator.next();

            ichatmutablecomponent.append(Entity.removeAction(ichatbasecomponent1));
        }

        return ichatmutablecomponent;
    }

    @Override
    public Component getName() {
        Component ichatbasecomponent = this.getCustomName();

        return ichatbasecomponent != null ? Entity.removeAction(ichatbasecomponent) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    public void setYHeadRot(float headYaw) {}

    public void setYBodyRot(float bodyYaw) {}

    public boolean isAttackable() {
        return true;
    }

    public boolean skipAttackInteraction(Entity attacker) {
        return false;
    }

    public String toString() {
        String s = this.level() == null ? "~NULL~" : this.level().toString();

        return this.removalReason != null ? String.format(Locale.ROOT, "%s['%s'/%d, uuid='%s', l='%s', x=%.2f, y=%.2f, z=%.2f, cpos=%s, tl=%d, v=%b, removed=%s]", this.getClass().getSimpleName(), this.getName().getString(), this.id, this.uuid, s, this.getX(), this.getY(), this.getZ(), this.chunkPosition(), this.tickCount, this.valid, this.removalReason) : String.format(Locale.ROOT, "%s['%s'/%d, uuid='%s', l='%s', x=%.2f, y=%.2f, z=%.2f, cpos=%s, tl=%d, v=%b]", this.getClass().getSimpleName(), this.getName().getString(), this.id, this.uuid, s, this.getX(), this.getY(), this.getZ(), this.chunkPosition(), this.tickCount, this.valid); // Paper - add more info
    }

    public boolean isInvulnerableTo(DamageSource damageSource) {
        return this.isRemoved() || this.invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !damageSource.isCreativePlayer() || damageSource.is(DamageTypeTags.IS_FIRE) && this.fireImmune() || damageSource.is(DamageTypeTags.IS_FALL) && this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public void copyPosition(Entity entity) {
        this.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    public void restoreFrom(Entity original) {
        CompoundTag nbttagcompound = original.saveWithoutId(new CompoundTag());

        nbttagcompound.remove("Dimension");
        this.load(nbttagcompound);
        this.portalCooldown = original.portalCooldown;
        this.portalProcess = original.portalProcess;
    }

    @Nullable
    public Entity changeDimension(DimensionTransition teleportTarget) {
        Level world = this.level();

        // Paper start - Fix item duplication and teleport issues
        if (!this.isAlive() || !this.valid) {
            LOGGER.warn("Illegal Entity Teleport " + this + " to " + teleportTarget.newLevel() + ":" + teleportTarget.pos(), new Throwable());
            return null;
        }
        // Paper end - Fix item duplication and teleport issues
        if (world instanceof ServerLevel worldserver) {
            if (!this.isRemoved()) {
                // CraftBukkit start
                Location to = new Location(teleportTarget.newLevel().getWorld(), teleportTarget.pos().x, teleportTarget.pos().y, teleportTarget.pos().z, teleportTarget.yRot(), teleportTarget.xRot());
                // Paper start - gateway-specific teleport event
                final EntityTeleportEvent teleEvent;
                if (this.portalProcess != null && this.portalProcess.isSamePortal(((net.minecraft.world.level.block.EndGatewayBlock) net.minecraft.world.level.block.Blocks.END_GATEWAY)) && this.level.getBlockEntity(this.portalProcess.getEntryPosition()) instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
                    teleEvent = new com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent(this.getBukkitEntity(), this.getBukkitEntity().getLocation(), to, new org.bukkit.craftbukkit.block.CraftEndGateway(to.getWorld(), theEndGatewayBlockEntity));
                    teleEvent.callEvent();
                } else {
                    teleEvent = CraftEventFactory.callEntityTeleportEvent(this, to);
                }
                // Paper end - gateway-specific teleport event
                if (teleEvent.isCancelled() || teleEvent.getTo() == null) {
                    return null;
                }
                to = teleEvent.getTo();
                teleportTarget = new DimensionTransition(((CraftWorld) to.getWorld()).getHandle(), CraftLocation.toVec3D(to), teleportTarget.speed(), to.getYaw(), to.getPitch(), teleportTarget.missingRespawnBlock(), teleportTarget.postDimensionTransition(), teleportTarget.cause());
                // CraftBukkit end
                ServerLevel worldserver1 = teleportTarget.newLevel();
                List<Entity> list = this.getPassengers();

                this.unRide();
                List<Entity> list1 = new ArrayList();
                Iterator iterator = list.iterator();

                Entity entity;

                while (iterator.hasNext()) {
                    Entity entity1 = (Entity) iterator.next();

                    entity = entity1.changeDimension(teleportTarget);
                    if (entity != null) {
                        list1.add(entity);
                    }
                }

                worldserver.getProfiler().push("changeDimension");
                Entity entity2 = worldserver1.dimension() == worldserver.dimension() ? this : this.getType().create(worldserver1);

                if (entity2 != null) {
                    if (this != entity2) {
                        // Paper start - Fix item duplication and teleport issues
                        if (this instanceof Leashable leashable) {
                            leashable.dropLeash(true, true); // Paper drop lead
                        }
                        // Paper end - Fix item duplication and teleport issues
                        entity2.restoreFrom(this);
                        this.removeAfterChangingDimensions();
                        // CraftBukkit start - Forward the CraftEntity to the new entity
                        this.getBukkitEntity().setHandle(entity2);
                        entity2.bukkitEntity = this.getBukkitEntity();
                        // CraftBukkit end
                    }

                    entity2.moveTo(teleportTarget.pos().x, teleportTarget.pos().y, teleportTarget.pos().z, teleportTarget.yRot(), entity2.getXRot());
                    entity2.setDeltaMovement(teleportTarget.speed());
                    if (this != entity2 && this.inWorld) { // CraftBukkit - Don't spawn the new entity if the current entity isn't spawned
                        worldserver1.addDuringTeleport(entity2);
                    }

                    Iterator iterator1 = list1.iterator();

                    while (iterator1.hasNext()) {
                        entity = (Entity) iterator1.next();
                        entity.startRiding(entity2, true);
                    }

                    worldserver.resetEmptyTime();
                    worldserver1.resetEmptyTime();
                    teleportTarget.postDimensionTransition().onTransition(entity2);
                }

                worldserver.getProfiler().pop();
                return entity2;
            }
        }

        return null;
    }

    public void placePortalTicket(BlockPos pos) {
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            worldserver.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
        }

    }

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION, null); // CraftBukkit - add Bukkit remove cause
        if (this instanceof Leashable leashable && leashable.isLeashed()) { // Paper - only call if it is leashed
            // Paper start - Expand EntityUnleashEvent
            final EntityUnleashEvent event = new EntityUnleashEvent(this.getBukkitEntity(), UnleashReason.UNKNOWN, false); // CraftBukkit
            event.callEvent();
            leashable.dropLeash(true, event.isDropLeash());
            // Paper end - Expand EntityUnleashEvent
        }

    }

    public Vec3 getRelativePortalPosition(Direction.Axis portalAxis, BlockUtil.FoundRectangle portalRect) {
        return PortalShape.getRelativePosition(portalRect, portalAxis, this.position(), this.getDimensions(this.getPose()));
    }

    // CraftBukkit start
    public CraftPortalEvent callPortalEvent(Entity entity, Location exit, PlayerTeleportEvent.TeleportCause cause, int searchRadius, int creationRadius) {
        org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
        Location enter = bukkitEntity.getLocation();

        EntityPortalEvent event = new EntityPortalEvent(bukkitEntity, enter, exit, searchRadius, true, creationRadius);
        event.getEntity().getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null || !entity.isAlive()) {
            return null;
        }
        return new CraftPortalEvent(event);
    }
    // CraftBukkit end

    public boolean canUsePortal(boolean allowVehicles) {
        return (allowVehicles || !this.isPassenger()) && this.isAlive();
    }

    public boolean canChangeDimensions(Level from, Level to) {
        return this.isAlive() && this.valid; // Paper - Fix item duplication and teleport issues
    }

    public float getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState, float max) {
        return max;
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float explosionPower) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory section) {
        section.setDetail("Entity Type", () -> {
            String s = String.valueOf(EntityType.getKey(this.getType()));

            return s + " (" + this.getClass().getCanonicalName() + ")";
        });
        section.setDetail("Entity ID", (Object) this.id);
        section.setDetail("Entity Name", () -> {
            return this.getName().getString();
        });
        section.setDetail("Entity's Exact location", (Object) String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        section.setDetail("Entity's Block location", (Object) CrashReportCategory.formatLocation(this.level(), Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ())));
        Vec3 vec3d = this.getDeltaMovement();

        section.setDetail("Entity's Momentum", (Object) String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3d.x, vec3d.y, vec3d.z));
        section.setDetail("Entity's Passengers", () -> {
            return this.getPassengers().toString();
        });
        section.setDetail("Entity's Vehicle", () -> {
            return String.valueOf(this.getVehicle());
        });
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
        this.stringUUID = this.uuid.toString();
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    @Override
    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return Entity.viewScale;
    }

    public static void setViewScale(double value) {
        Entity.viewScale = value;
    }

    @Override
    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName()).withStyle((chatmodifier) -> {
            return chatmodifier.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID());
        });
    }

    public void setCustomName(@Nullable Component name) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME, Optional.ofNullable(name));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return (Component) ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).orElse((Object) null);
    }

    @Override
    public boolean hasCustomName() {
        return ((Optional) this.entityData.get(Entity.DATA_CUSTOM_NAME)).isPresent();
    }

    public void setCustomNameVisible(boolean visible) {
        this.entityData.set(Entity.DATA_CUSTOM_NAME_VISIBLE, visible);
    }

    public boolean isCustomNameVisible() {
        return (Boolean) this.entityData.get(Entity.DATA_CUSTOM_NAME_VISIBLE);
    }

    // CraftBukkit start
    public boolean teleportTo(ServerLevel worldserver, double d0, double d1, double d2, Set<RelativeMovement> set, float f, float f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return this.teleportTo(worldserver, d0, d1, d2, set, f, f1);
    }
    // CraftBukkit end

    public boolean teleportTo(ServerLevel world, double destX, double destY, double destZ, Set<RelativeMovement> flags, float yaw, float pitch) {
        float f2 = Mth.clamp(pitch, -90.0F, 90.0F);

        if (world == this.level()) {
            this.moveTo(destX, destY, destZ, yaw, f2);
            this.teleportPassengers();
            this.setYHeadRot(yaw);
        } else {
            this.unRide();
            Entity entity = this.getType().create(world);

            if (entity == null) {
                return false;
            }

            entity.restoreFrom(this);
            entity.moveTo(destX, destY, destZ, yaw, f2);
            entity.setYHeadRot(yaw);
            this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION, null); // CraftBukkit - add Bukkit remove cause
            // CraftBukkit start - Don't spawn the new entity if the current entity isn't spawned
            if (this.inWorld) {
                world.addDuringTeleport(entity);
            }
            // CraftBukkit end
        }

        return true;
    }

    public void dismountTo(double destX, double destY, double destZ) {
        this.teleportTo(destX, destY, destZ);
    }

    public void teleportTo(double destX, double destY, double destZ) {
        if (this.level() instanceof ServerLevel) {
            this.moveTo(destX, destY, destZ, this.getYRot(), this.getXRot());
            this.teleportPassengers();
        }
    }

    private void teleportPassengers() {
        this.getSelfAndPassengers().forEach((entity) -> {
            UnmodifiableIterator unmodifiableiterator = entity.passengers.iterator();

            while (unmodifiableiterator.hasNext()) {
                Entity entity1 = (Entity) unmodifiableiterator.next();

                entity.positionRider(entity1, Entity::moveTo);
            }

        });
    }

    public void teleportRelative(double offsetX, double offsetY, double offsetZ) {
        this.teleportTo(this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ);
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> entries) {}

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Entity.DATA_POSE.equals(data)) {
            this.refreshDimensions();
        }

    }

    /** @deprecated */
    @Deprecated
    protected void fixupDimensions() {
        net.minecraft.world.entity.Pose entitypose = this.getPose();
        EntityDimensions entitysize = this.getDimensions(entitypose);

        this.dimensions = entitysize;
        this.eyeHeight = entitysize.eyeHeight();
    }

    public void refreshDimensions() {
        EntityDimensions entitysize = this.dimensions;
        net.minecraft.world.entity.Pose entitypose = this.getPose();
        EntityDimensions entitysize1 = this.getDimensions(entitypose);

        this.dimensions = entitysize1;
        this.eyeHeight = entitysize1.eyeHeight();
        this.reapplyPosition();
        boolean flag = (double) entitysize1.width() <= 4.0D && (double) entitysize1.height() <= 4.0D;

        if (!this.level.isClientSide && !this.firstTick && !this.noPhysics && flag && (entitysize1.width() > entitysize.width() || entitysize1.height() > entitysize.height()) && !(this instanceof Player)) {
            this.fudgePositionAfterSizeChange(entitysize);
        }

    }

    public boolean fudgePositionAfterSizeChange(EntityDimensions previous) {
        EntityDimensions entitysize1 = this.getDimensions(this.getPose());
        Vec3 vec3d = this.position().add(0.0D, (double) previous.height() / 2.0D, 0.0D);
        double d0 = (double) Math.max(0.0F, entitysize1.width() - previous.width()) + 1.0E-6D;
        double d1 = (double) Math.max(0.0F, entitysize1.height() - previous.height()) + 1.0E-6D;
        VoxelShape voxelshape = Shapes.create(AABB.ofSize(vec3d, d0, d1, d0));
        Optional<Vec3> optional = this.level.findFreePosition(this, voxelshape, vec3d, (double) entitysize1.width(), (double) entitysize1.height(), (double) entitysize1.width());

        if (optional.isPresent()) {
            this.setPos(((Vec3) optional.get()).add(0.0D, (double) (-entitysize1.height()) / 2.0D, 0.0D));
            return true;
        } else {
            if (entitysize1.width() > previous.width() && entitysize1.height() > previous.height()) {
                VoxelShape voxelshape1 = Shapes.create(AABB.ofSize(vec3d, d0, 1.0E-6D, d0));
                Optional<Vec3> optional1 = this.level.findFreePosition(this, voxelshape1, vec3d, (double) entitysize1.width(), (double) previous.height(), (double) entitysize1.width());

                if (optional1.isPresent()) {
                    this.setPos(((Vec3) optional1.get()).add(0.0D, (double) (-previous.height()) / 2.0D + 1.0E-6D, 0.0D));
                    return true;
                }
            }

            return false;
        }
    }

    public Direction getDirection() {
        return Direction.fromYRot((double) this.getYRot());
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer spectator) {
        return true;
    }

    @Override
    public final AABB getBoundingBox() {
        return this.bb;
    }

    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox();
    }

    public final void setBoundingBox(AABB boundingBox) {
        // CraftBukkit start - block invalid bounding boxes
        double minX = boundingBox.minX,
                minY = boundingBox.minY,
                minZ = boundingBox.minZ,
                maxX = boundingBox.maxX,
                maxY = boundingBox.maxY,
                maxZ = boundingBox.maxZ;
        double len = boundingBox.maxX - boundingBox.minX;
        if (len < 0) maxX = minX;
        if (len > 64) maxX = minX + 64.0;

        len = boundingBox.maxY - boundingBox.minY;
        if (len < 0) maxY = minY;
        if (len > 64) maxY = minY + 64.0;

        len = boundingBox.maxZ - boundingBox.minZ;
        if (len < 0) maxZ = minZ;
        if (len > 64) maxZ = minZ + 64.0;
        this.bb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        // CraftBukkit end
    }

    public final float getEyeHeight(net.minecraft.world.entity.Pose pose) {
        return this.getDimensions(pose).eyeHeight();
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public Vec3 getLeashOffset(float tickDelta) {
        return this.getLeashOffset();
    }

    protected Vec3 getLeashOffset() {
        return new Vec3(0.0D, (double) this.getEyeHeight(), (double) (this.getBbWidth() * 0.4F));
    }

    public SlotAccess getSlot(int mappedIndex) {
        return SlotAccess.NULL;
    }

    @Override
    public void sendSystemMessage(Component message) {}

    public Level getCommandSenderWorld() {
        return this.level();
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level().getServer();
    }

    public InteractionResult interactAt(Player player, Vec3 hitPos, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    public void startSeenByPlayer(ServerPlayer player) {}

    public void stopSeenByPlayer(ServerPlayer player) {}

    public float rotate(Rotation rotation) {
        float f = Mth.wrapDegrees(this.getYRot());

        switch (rotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    public float mirror(Mirror mirror) {
        float f = Mth.wrapDegrees(this.getYRot());

        switch (mirror) {
            case FRONT_BACK:
                return -f;
            case LEFT_RIGHT:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public ProjectileDeflection deflection(Projectile projectile) {
        return this.getType().is(EntityTypeTags.DEFLECTS_PROJECTILES) ? ProjectileDeflection.REVERSE : ProjectileDeflection.NONE;
    }

    @Nullable
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    @Nullable
    public Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : (Entity) this.passengers.get(0);
    }

    public boolean hasPassenger(Entity passenger) {
        return this.passengers.contains(passenger);
    }

    public boolean hasPassenger(Predicate<Entity> predicate) {
        UnmodifiableIterator unmodifiableiterator = this.passengers.iterator();

        Entity entity;

        do {
            if (!unmodifiableiterator.hasNext()) {
                return false;
            }

            entity = (Entity) unmodifiableiterator.next();
        } while (!predicate.test(entity));

        return true;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        if (this.passengers.isEmpty()) { return Stream.of(); } // Paper - Optimize indirect passenger iteration
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    @Override
    public Stream<Entity> getSelfAndPassengers() {
        if (this.passengers.isEmpty()) { return Stream.of(this); } // Paper - Optimize indirect passenger iteration
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    @Override
    public Stream<Entity> getPassengersAndSelf() {
        if (this.passengers.isEmpty()) { return Stream.of(this); } // Paper - Optimize indirect passenger iteration
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        // Paper start - Optimize indirect passenger iteration
        if (this.passengers.isEmpty()) { return ImmutableList.of(); }
        ImmutableList.Builder<Entity> indirectPassengers = ImmutableList.builder();
        for (Entity passenger : this.passengers) {
            indirectPassengers.add(passenger);
            indirectPassengers.addAll(passenger.getIndirectPassengers());
        }
        return indirectPassengers.build();
    }
    private Iterable<Entity> getIndirectPassengers_old() {
        // Paper end - Optimize indirect passenger iteration
        return () -> {
            return this.getIndirectPassengersStream().iterator();
        };
    }

    public int countPlayerPassengers() {
        return (int) this.getIndirectPassengersStream().filter((entity) -> {
            return entity instanceof Player;
        }).count();
    }

    public boolean hasExactlyOnePlayerPassenger() {
        if (this.passengers.isEmpty()) { return false; } // Paper - Optimize indirect passenger iteration
        return this.countPlayerPassengers() == 1;
    }

    public Entity getRootVehicle() {
        Entity entity;

        for (entity = this; entity.isPassenger(); entity = entity.getVehicle()) {
            ;
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity entity) {
        return this.getRootVehicle() == entity.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity passenger) {
        if (!passenger.isPassenger()) {
            return false;
        } else {
            Entity entity1 = passenger.getVehicle();

            return entity1 == this ? true : this.hasIndirectPassenger(entity1);
        }
    }

    public boolean isControlledByLocalInstance() {
        net.minecraft.world.entity.LivingEntity entityliving = this.getControllingPassenger();

        if (entityliving instanceof Player entityhuman) {
            return entityhuman.isLocalPlayer();
        } else {
            return this.isEffectiveAi();
        }
    }

    public boolean isEffectiveAi() {
        return !this.level().isClientSide;
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double vehicleWidth, double passengerWidth, float passengerYaw) {
        double d2 = (vehicleWidth + passengerWidth + 9.999999747378752E-6D) / 2.0D;
        float f1 = -Mth.sin(passengerYaw * 0.017453292F);
        float f2 = Mth.cos(passengerYaw * 0.017453292F);
        float f3 = Math.max(Math.abs(f1), Math.abs(f2));

        return new Vec3((double) f1 * d2 / (double) f3, 0.0D, (double) f2 * d2 / (double) f3);
    }

    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    @Nullable
    public Entity getControlledVehicle() {
        return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    public int getFireImmuneTicks() {
        return 1;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(this, this.position(), this.getRotationVector(), this.level() instanceof ServerLevel ? (ServerLevel) this.level() : null, this.getPermissionLevel(), this.getName().getString(), this.getDisplayName(), this.level().getServer(), this);
    }

    protected int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int permissionLevel) {
        return this.getPermissionLevel() >= permissionLevel;
    }

    @Override
    public boolean acceptsSuccess() {
        return this.level().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }

    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Vec3 target) {
        Vec3 vec3d1 = anchorPoint.apply(this);
        double d0 = target.x - vec3d1.x;
        double d1 = target.y - vec3d1.y;
        double d2 = target.z - vec3d1.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);

        this.setXRot(Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 57.2957763671875D))));
        this.setYRot(Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public float getPreciseBodyRotation(float delta) {
        return Mth.lerp(delta, this.yRotO, this.yRot);
    }

    public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> tag, double speed) {
        if (this.touchingUnloadedChunk()) {
            return false;
        } else {
            AABB axisalignedbb = this.getBoundingBox().deflate(0.001D);
            int i = Mth.floor(axisalignedbb.minX);
            int j = Mth.ceil(axisalignedbb.maxX);
            int k = Mth.floor(axisalignedbb.minY);
            int l = Mth.ceil(axisalignedbb.maxY);
            int i1 = Mth.floor(axisalignedbb.minZ);
            int j1 = Mth.ceil(axisalignedbb.maxZ);
            double d1 = 0.0D;
            boolean flag = this.isPushedByFluid();
            boolean flag1 = false;
            Vec3 vec3d = Vec3.ZERO;
            int k1 = 0;
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int l1 = i; l1 < j; ++l1) {
                for (int i2 = k; i2 < l; ++i2) {
                    for (int j2 = i1; j2 < j1; ++j2) {
                        blockposition_mutableblockposition.set(l1, i2, j2);
                        FluidState fluid = this.level().getFluidState(blockposition_mutableblockposition);

                        if (fluid.is(tag)) {
                            double d2 = (double) ((float) i2 + fluid.getHeight(this.level(), blockposition_mutableblockposition));

                            if (d2 >= axisalignedbb.minY) {
                                flag1 = true;
                                d1 = Math.max(d2 - axisalignedbb.minY, d1);
                                if (flag) {
                                    Vec3 vec3d1 = fluid.getFlow(this.level(), blockposition_mutableblockposition);

                                    if (d1 < 0.4D) {
                                        vec3d1 = vec3d1.scale(d1);
                                    }

                                    vec3d = vec3d.add(vec3d1);
                                    ++k1;
                                }
                                // CraftBukkit start - store last lava contact location
                                if (tag == FluidTags.LAVA) {
                                    this.lastLavaContact = blockposition_mutableblockposition.immutable();
                                }
                                // CraftBukkit end
                            }
                        }
                    }
                }
            }

            if (vec3d.length() > 0.0D) {
                if (k1 > 0) {
                    vec3d = vec3d.scale(1.0D / (double) k1);
                }

                if (!(this instanceof Player)) {
                    vec3d = vec3d.normalize();
                }

                Vec3 vec3d2 = this.getDeltaMovement();

                vec3d = vec3d.scale(speed);
                double d3 = 0.003D;

                if (Math.abs(vec3d2.x) < 0.003D && Math.abs(vec3d2.z) < 0.003D && vec3d.length() < 0.0045000000000000005D) {
                    vec3d = vec3d.normalize().scale(0.0045000000000000005D);
                }

                this.setDeltaMovement(this.getDeltaMovement().add(vec3d));
            }

            this.fluidHeight.put(tag, d1);
            return flag1;
        }
    }

    public boolean touchingUnloadedChunk() {
        AABB axisalignedbb = this.getBoundingBox().inflate(1.0D);
        int i = Mth.floor(axisalignedbb.minX);
        int j = Mth.ceil(axisalignedbb.maxX);
        int k = Mth.floor(axisalignedbb.minZ);
        int l = Mth.ceil(axisalignedbb.maxZ);

        return !this.level().hasChunksAt(i, k, j, l);
    }

    public double getFluidHeight(TagKey<Fluid> fluid) {
        return this.fluidHeight.getDouble(fluid);
    }

    public double getFluidJumpThreshold() {
        return (double) this.getEyeHeight() < 0.4D ? 0.0D : 0.4D;
    }

    public final float getBbWidth() {
        return this.dimensions.width();
    }

    public final float getBbHeight() {
        return this.dimensions.height();
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        return new ClientboundAddEntityPacket(this, entityTrackerEntry);
    }

    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return this.type.getDimensions();
    }

    public final EntityAttachments getAttachments() {
        return this.dimensions.attachments();
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 trackingPosition() {
        return this.position();
    }

    @Override
    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    public BlockState getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.level().getBlockState(this.blockPosition());
        }

        return this.inBlockState;
    }

    public ChunkPos chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 velocity) {
        this.deltaMovement = velocity;
    }

    public void addDeltaMovement(Vec3 velocity) {
        this.setDeltaMovement(this.getDeltaMovement().add(velocity));
    }

    public void setDeltaMovement(double x, double y, double z) {
        this.setDeltaMovement(new Vec3(x, y, z));
    }

    public final int getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double widthScale) {
        return this.position.x + (double) this.getBbWidth() * widthScale;
    }

    public double getRandomX(double widthScale) {
        return this.getX((2.0D * this.random.nextDouble() - 1.0D) * widthScale);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double heightScale) {
        return this.position.y + (double) this.getBbHeight() * heightScale;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + (double) this.eyeHeight;
    }

    public final int getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double widthScale) {
        return this.position.z + (double) this.getBbWidth() * widthScale;
    }

    public double getRandomZ(double widthScale) {
        return this.getZ((2.0D * this.random.nextDouble() - 1.0D) * widthScale);
    }

    // Paper start - Block invalid positions and bounding box
    public static boolean checkPosition(Entity entity, double newX, double newY, double newZ) {
        if (Double.isFinite(newX) && Double.isFinite(newY) && Double.isFinite(newZ)) {
            return true;
        }

        String entityInfo;
        try {
            entityInfo = entity.toString();
        } catch (Exception ex) {
            entityInfo = "[Entity info unavailable] ";
        }
        LOGGER.error("New entity position is invalid! Tried to set invalid position ({},{},{}) for entity {} located at {}, entity info: {}", newX, newY, newZ, entity.getClass().getName(), entity.position, entityInfo, new Throwable());
        return false;
    }
    public final void setPosRaw(double x, double y, double z) {
        this.setPosRaw(x, y, z, false);
    }
    public final void setPosRaw(double x, double y, double z, boolean forceBoundingBoxUpdate) {
        if (!checkPosition(this, x, y, z)) {
            return;
        }
        // Paper end - Block invalid positions and bounding box
        // Paper start - Fix MC-4
        if (this instanceof ItemEntity) {
            if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.fixEntityPositionDesync) {
                // encode/decode from ClientboundMoveEntityPacket
                x = Mth.lfloor(x * 4096.0) * (1 / 4096.0);
                y = Mth.lfloor(y * 4096.0) * (1 / 4096.0);
                z = Mth.lfloor(z * 4096.0) * (1 / 4096.0);
            }
        }
        // Paper end - Fix MC-4
        if (this.position.x != x || this.position.y != y || this.position.z != z) {
            this.position = new Vec3(x, y, z);
            int i = Mth.floor(x);
            int j = Mth.floor(y);
            int k = Mth.floor(z);

            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(i, j, k);
                this.inBlockState = null;
                if (SectionPos.blockToSectionCoord(i) != this.chunkPosition.x || SectionPos.blockToSectionCoord(k) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkPos(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
        }

        // Paper start - Block invalid positions and bounding box; don't allow desync of pos and AABB
        // hanging has its own special logic
        if (!(this instanceof net.minecraft.world.entity.decoration.HangingEntity) && (forceBoundingBoxUpdate || this.position.x != x || this.position.y != y || this.position.z != z)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
        // Paper end - Block invalid positions and bounding box
    }

    public void checkDespawn() {}

    public Vec3 getRopeHoldPosition(float delta) {
        return this.getPosition(delta).add(0.0D, (double) this.eyeHeight * 0.7D, 0.0D);
    }

    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        int i = packet.getId();
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();

        this.syncPacketPositionCodec(d0, d1, d2);
        this.moveTo(d0, d1, d2);
        this.setXRot(packet.getXRot());
        this.setYRot(packet.getYRot());
        this.setId(i);
        this.setUUID(packet.getUUID());
    }

    @Nullable
    public ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean inPowderSnow) {
        this.isInPowderSnow = inPowderSnow;
    }

    public boolean canFreeze() {
        return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return (this.isInPowderSnow || this.wasInPowderSnow) && this.canFreeze();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    public void setYRot(float yaw) {
        if (!Float.isFinite(yaw)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yaw + ", discarding.");
        } else {
            this.yRot = yaw;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    public void setXRot(float pitch) {
        if (!Float.isFinite(pitch)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + pitch + ", discarding.");
        } else {
            this.xRot = pitch;
        }
    }

    public boolean canSprint() {
        return false;
    }

    public float maxUpStep() {
        return 0.0F;
    }

    public void onExplosionHit(@Nullable Entity entity) {}

    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    @Nullable
    public Entity.RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    @Override
    public final void setRemoved(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.setRemoved(reason, null);
    }

    @Override
    public final void setRemoved(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        CraftEventFactory.callEntityRemoveEvent(this, cause);
        // CraftBukkit end
        if (this.removalReason == null) {
            this.removalReason = entity_removalreason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        this.getPassengers().forEach(Entity::stopRiding);
        this.levelCallback.onRemove(entity_removalreason);
    }

    public void unsetRemoved() {
        this.removalReason = null;
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback changeListener) {
        this.levelCallback = changeListener;
    }

    @Override
    public boolean shouldBeSaved() {
        return this.removalReason != null && !this.removalReason.shouldSave() ? false : (this.isPassenger() ? false : !this.isVehicle() || !this.hasExactlyOnePlayerPassenger());
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(Level world, BlockPos pos) {
        return true;
    }

    public Level level() {
        return this.level;
    }

    public void setLevel(Level world) {
        this.level = world;
    }

    public DamageSources damageSources() {
        return this.level().damageSources();
    }

    public RegistryAccess registryAccess() {
        return this.level().registryAccess();
    }

    protected void lerpPositionAndRotationStep(int step, double x, double y, double z, double yaw, double pitch) {
        double d5 = 1.0D / (double) step;
        double d6 = Mth.lerp(d5, this.getX(), x);
        double d7 = Mth.lerp(d5, this.getY(), y);
        double d8 = Mth.lerp(d5, this.getZ(), z);
        float f = (float) Mth.rotLerp(d5, (double) this.getYRot(), yaw);
        float f1 = (float) Mth.lerp(d5, (double) this.getXRot(), pitch);

        this.setPos(d6, d7, d8);
        this.setRot(f, f1);
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public Vec3 getKnownMovement() {
        net.minecraft.world.entity.LivingEntity entityliving = this.getControllingPassenger();

        if (entityliving instanceof Player entityhuman) {
            if (this.isAlive()) {
                return entityhuman.getKnownMovement();
            }
        }

        return this.getDeltaMovement();
    }

    @Nullable
    public ItemStack getWeaponItem() {
        return null;
    }

    public static enum RemovalReason {

        KILLED(true, false), DISCARDED(true, false), UNLOADED_TO_CHUNK(false, true), UNLOADED_WITH_PLAYER(false, false), CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        private RemovalReason(final boolean flag, final boolean flag1) {
            this.destroy = flag;
            this.save = flag1;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }

    public static enum MovementEmission {

        NONE(false, false), SOUNDS(true, false), EVENTS(false, true), ALL(true, true);

        final boolean sounds;
        final boolean events;

        private MovementEmission(final boolean flag, final boolean flag1) {
            this.sounds = flag;
            this.events = flag1;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    @FunctionalInterface
    public interface MoveFunction {

        void accept(Entity entity, double x, double y, double z);
    }

    // Paper start - Expose entity id counter
    public static int nextEntityId() {
        return ENTITY_COUNTER.incrementAndGet();
    }

    public boolean isTicking() {
        return ((net.minecraft.server.level.ServerChunkCache) level.getChunkSource()).isPositionTicking(this);
    }
    // Paper end - Expose entity id counter
}
