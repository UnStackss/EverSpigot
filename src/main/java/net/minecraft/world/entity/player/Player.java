package net.minecraft.world.entity.player;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
// CraftBukkit end

public abstract class Player extends LivingEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final HumanoidArm DEFAULT_MAIN_HAND = HumanoidArm.RIGHT;
    public static final int DEFAULT_MODEL_CUSTOMIZATION = 0;
    public static final int MAX_HEALTH = 20;
    public static final int SLEEP_DURATION = 100;
    public static final int WAKE_UP_DURATION = 10;
    public static final int ENDER_SLOT_OFFSET = 200;
    public static final int HELD_ITEM_SLOT = 499;
    public static final int CRAFTING_SLOT_OFFSET = 500;
    public static final float DEFAULT_BLOCK_INTERACTION_RANGE = 4.5F;
    public static final float DEFAULT_ENTITY_INTERACTION_RANGE = 3.0F;
    public static final float CROUCH_BB_HEIGHT = 1.5F;
    public static final float SWIMMING_BB_WIDTH = 0.6F;
    public static final float SWIMMING_BB_HEIGHT = 0.6F;
    public static final float DEFAULT_EYE_HEIGHT = 1.62F;
    private static final int CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME_TICKS = 40;
    public static final Vec3 DEFAULT_VEHICLE_ATTACHMENT = new Vec3(0.0D, 0.6D, 0.0D);
    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F).withEyeHeight(1.62F).withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, Player.DEFAULT_VEHICLE_ATTACHMENT));
    // CraftBukkit - decompile error
    private static final Map<Pose, EntityDimensions> POSES = ImmutableMap.<Pose, EntityDimensions>builder().put(Pose.STANDING, Player.STANDING_DIMENSIONS).put(Pose.SLEEPING, Player.SLEEPING_DIMENSIONS).put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F)).put(Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F)).put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F)).put(Pose.CROUCHING, EntityDimensions.scalable(0.6F, 1.5F).withEyeHeight(1.27F).withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, Player.DEFAULT_VEHICLE_ATTACHMENT))).put(Pose.DYING, EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(1.62F)).build();
    private static final EntityDataAccessor<Float> DATA_PLAYER_ABSORPTION_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SCORE_ID = SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> DATA_PLAYER_MODE_CUSTOMISATION = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_PLAYER_MAIN_HAND = SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<CompoundTag> DATA_SHOULDER_LEFT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);
    protected static final EntityDataAccessor<CompoundTag> DATA_SHOULDER_RIGHT = SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);
    private long timeEntitySatOnShoulder;
    final Inventory inventory = new Inventory(this);
    protected PlayerEnderChestContainer enderChestInventory = new PlayerEnderChestContainer(this); // CraftBukkit - add "this" to constructor
    public final InventoryMenu inventoryMenu;
    public AbstractContainerMenu containerMenu;
    protected FoodData foodData = new FoodData(this); // CraftBukkit - add "this" to constructor
    protected int jumpTriggerTime;
    public float oBob;
    public float bob;
    public int takeXpDelay;
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public int sleepCounter;
    protected boolean wasUnderwater;
    private final Abilities abilities = new Abilities();
    public int experienceLevel;
    public int totalExperience;
    public float experienceProgress;
    public int enchantmentSeed;
    protected final float defaultFlySpeed = 0.02F;
    private int lastLevelUpTime;
    public GameProfile gameProfile;
    private boolean reducedDebugInfo;
    private ItemStack lastItemInMainHand;
    private final ItemCooldowns cooldowns;
    private Optional<GlobalPos> lastDeathLocation;
    @Nullable
    public FishingHook fishing;
    public float hurtDir; // Paper - protected -> public
    @Nullable
    public Vec3 currentImpulseImpactPos;
    @Nullable
    public Entity currentExplosionCause;
    private boolean ignoreFallDamageFromCurrentImpulse;
    private int currentImpulseContextResetGraceTime;
    public boolean affectsSpawning = true; // Paper - Affects Spawning API
    public net.kyori.adventure.util.TriState flyingFallDamage = net.kyori.adventure.util.TriState.NOT_SET; // Paper - flying fall damage

    // CraftBukkit start
    public boolean fauxSleeping;
    public int oldLevel = -1;

    @Override
    public CraftHumanEntity getBukkitEntity() {
        return (CraftHumanEntity) super.getBukkitEntity();
    }
    // CraftBukkit end

    public Player(Level world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(EntityType.PLAYER, world);
        this.lastItemInMainHand = ItemStack.EMPTY;
        this.cooldowns = this.createItemCooldowns();
        this.lastDeathLocation = Optional.empty();
        this.setUUID(gameProfile.getId());
        this.gameProfile = gameProfile;
        this.inventoryMenu = new InventoryMenu(this.inventory, !world.isClientSide, this);
        this.containerMenu = this.inventoryMenu;
        this.moveTo((double) pos.getX() + 0.5D, (double) (pos.getY() + 1), (double) pos.getZ() + 0.5D, yaw, 0.0F);
        this.rotOffs = 180.0F;
    }

    public boolean blockActionRestricted(Level world, BlockPos pos, GameType gameMode) {
        if (!gameMode.isBlockPlacingRestricted()) {
            return false;
        } else if (gameMode == GameType.SPECTATOR) {
            return true;
        } else if (this.mayBuild()) {
            return false;
        } else {
            ItemStack itemstack = this.getMainHandItem();

            return itemstack.isEmpty() || !itemstack.canBreakBlockInAdventureMode(new BlockInWorld(world, pos, false));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.MOVEMENT_SPEED, 0.10000000149011612D).add(Attributes.ATTACK_SPEED).add(Attributes.LUCK).add(Attributes.BLOCK_INTERACTION_RANGE, 4.5D).add(Attributes.ENTITY_INTERACTION_RANGE, 3.0D).add(Attributes.BLOCK_BREAK_SPEED).add(Attributes.SUBMERGED_MINING_SPEED).add(Attributes.SNEAKING_SPEED).add(Attributes.MINING_EFFICIENCY).add(Attributes.SWEEPING_DAMAGE_RATIO);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Player.DATA_PLAYER_ABSORPTION_ID, 0.0F);
        builder.define(Player.DATA_SCORE_ID, 0);
        builder.define(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0);
        builder.define(Player.DATA_PLAYER_MAIN_HAND, (byte) Player.DEFAULT_MAIN_HAND.getId());
        builder.define(Player.DATA_SHOULDER_LEFT, new CompoundTag());
        builder.define(Player.DATA_SHOULDER_RIGHT, new CompoundTag());
    }

    @Override
    public void tick() {
        this.noPhysics = this.isSpectator();
        if (this.isSpectator()) {
            this.setOnGround(false);
        }

        if (this.takeXpDelay > 0) {
            --this.takeXpDelay;
        }

        if (this.isSleeping()) {
            ++this.sleepCounter;
            // Paper start - Add PlayerDeepSleepEvent
            if (this.sleepCounter == SLEEP_DURATION) {
                if (!new io.papermc.paper.event.player.PlayerDeepSleepEvent((org.bukkit.entity.Player) getBukkitEntity()).callEvent()) {
                    this.sleepCounter = Integer.MIN_VALUE;
                }
            }
            // Paper end - Add PlayerDeepSleepEvent
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
            }

            if (!this.level().isClientSide && this.level().isDay()) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            ++this.sleepCounter;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();
        super.tick();
        if (!this.level().isClientSide && this.containerMenu != null && !this.containerMenu.stillValid(this)) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.CANT_USE); // Paper - Inventory close reason
            this.containerMenu = this.inventoryMenu;
        }

        this.moveCloak();
        if (!this.level().isClientSide) {
            this.foodData.tick(this);
            this.awardStat(Stats.PLAY_TIME);
            this.awardStat(Stats.TOTAL_WORLD_TIME);
            if (this.isAlive()) {
                this.awardStat(Stats.TIME_SINCE_DEATH);
            }

            if (this.isDiscrete()) {
                this.awardStat(Stats.CROUCH_TIME);
            }

            if (!this.isSleeping()) {
                this.awardStat(Stats.TIME_SINCE_REST);
            }
        }

        int i = 29999999;
        double d0 = Mth.clamp(this.getX(), -2.9999999E7D, 2.9999999E7D);
        double d1 = Mth.clamp(this.getZ(), -2.9999999E7D, 2.9999999E7D);

        if (d0 != this.getX() || d1 != this.getZ()) {
            this.setPos(d0, this.getY(), d1);
        }

        ++this.attackStrengthTicker;
        ItemStack itemstack = this.getMainHandItem();

        if (!ItemStack.matches(this.lastItemInMainHand, itemstack)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, itemstack)) {
                this.resetAttackStrengthTicker();
            }

            this.lastItemInMainHand = itemstack.copy();
        }

        this.turtleHelmetTick();
        this.cooldowns.tick();
        this.updatePlayerPose();
        if (this.currentImpulseContextResetGraceTime > 0) {
            --this.currentImpulseContextResetGraceTime;
        }

    }

    @Override
    protected float getMaxHeadRotationRelativeToBody() {
        return this.isBlocking() ? 15.0F : super.getMaxHeadRotationRelativeToBody();
    }

    public boolean isSecondaryUseActive() {
        return this.isShiftKeyDown();
    }

    protected boolean wantsToStopRiding() {
        return this.isShiftKeyDown();
    }

    protected boolean isStayingOnGroundSurface() {
        return this.isShiftKeyDown();
    }

    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    private void turtleHelmetTick() {
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);

        if (itemstack.is(Items.TURTLE_HELMET) && !this.isEyeInFluid(FluidTags.WATER)) {
            this.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, false, false, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TURTLE_HELMET); // CraftBukkit
        }

    }

    protected ItemCooldowns createItemCooldowns() {
        return new ItemCooldowns();
    }

    private void moveCloak() {
        this.xCloakO = this.xCloak;
        this.yCloakO = this.yCloak;
        this.zCloakO = this.zCloak;
        double d0 = this.getX() - this.xCloak;
        double d1 = this.getY() - this.yCloak;
        double d2 = this.getZ() - this.zCloak;
        double d3 = 10.0D;

        if (d0 > 10.0D) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 > 10.0D) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 > 10.0D) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        if (d0 < -10.0D) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 < -10.0D) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 < -10.0D) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        this.xCloak += d0 * 0.25D;
        this.zCloak += d2 * 0.25D;
        this.yCloak += d1 * 0.25D;
    }

    protected void updatePlayerPose() {
        if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.SWIMMING)) {
            Pose entitypose;

            if (this.isFallFlying()) {
                entitypose = Pose.FALL_FLYING;
            } else if (this.isSleeping()) {
                entitypose = Pose.SLEEPING;
            } else if (this.isSwimming()) {
                entitypose = Pose.SWIMMING;
            } else if (this.isAutoSpinAttack()) {
                entitypose = Pose.SPIN_ATTACK;
            } else if (this.isShiftKeyDown() && !this.abilities.flying) {
                entitypose = Pose.CROUCHING;
            } else {
                entitypose = Pose.STANDING;
            }

            Pose entitypose1;

            if (!this.isSpectator() && !this.isPassenger() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(entitypose)) {
                if (this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)) {
                    entitypose1 = Pose.CROUCHING;
                } else {
                    entitypose1 = Pose.SWIMMING;
                }
            } else {
                entitypose1 = entitypose;
            }

            this.setPose(entitypose1);
        }
    }

    protected boolean canPlayerFitWithinBlocksAndEntitiesWhen(Pose pose) {
        return this.level().noCollision(this, this.getDimensions(pose).makeBoundingBox(this.position()).deflate(1.0E-7D));
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(this, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
    }

    public void playNotifySound(SoundEvent sound, SoundSource category, float volume, float pitch) {}

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public int getFireImmuneTicks() {
        return 20;
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 9) {
            this.completeUsingItem();
        } else if (status == 23) {
            this.reducedDebugInfo = false;
        } else if (status == 22) {
            this.reducedDebugInfo = true;
        } else {
            super.handleEntityEvent(status);
        }

    }

    // Paper start - Inventory close reason; unused code, but to keep signatures aligned
    public void closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        closeContainer();
        this.containerMenu = this.inventoryMenu;
    }
    // Paper end - Inventory close reason
    // Paper start - special close for unloaded inventory
    public void closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        this.containerMenu = this.inventoryMenu;
    }
    // Paper end - special close for unloaded inventory

    public void closeContainer() {
        this.containerMenu = this.inventoryMenu;
    }

    protected void doCloseContainer() {}

    @Override
    public void rideTick() {
        if (!this.level().isClientSide && this.wantsToStopRiding() && this.isPassenger()) {
            this.stopRiding();
            // CraftBukkit start - SPIGOT-7316: no longer passenger, dismount and return
            if (!this.isPassenger()) {
                this.setShiftKeyDown(false);
                return;
            }
        }
        {
            // CraftBukkit end
            super.rideTick();
            this.oBob = this.bob;
            this.bob = 0.0F;
        }
    }

    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        this.updateSwingTime();
        this.yHeadRot = this.getYRot();
    }

    @Override
    public void aiStep() {
        if (this.jumpTriggerTime > 0) {
            --this.jumpTriggerTime;
        }

        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            if (this.getHealth() < this.getMaxHealth() && this.tickCount % 20 == 0) {
                // CraftBukkit - added regain reason of "REGEN" for filtering purposes.
                this.heal(1.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN);
            }

            if (this.foodData.getSaturationLevel() < 20.0F && this.tickCount % 20 == 0) {
                this.foodData.setSaturation(this.foodData.getSaturationLevel() + 1.0F);
            }

            if (this.foodData.needsFood() && this.tickCount % 10 == 0) {
                this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);
            }
        }

        this.inventory.tick();
        this.oBob = this.bob;
        super.aiStep();
        this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED));
        float f;

        if (this.onGround() && !this.isDeadOrDying() && !this.isSwimming()) {
            f = Math.min(0.1F, (float) this.getDeltaMovement().horizontalDistance());
        } else {
            f = 0.0F;
        }

        this.bob += (f - this.bob) * 0.4F;
        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            AABB axisalignedbb;

            if (this.isPassenger() && !this.getVehicle().isRemoved()) {
                axisalignedbb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0D, 0.0D, 1.0D);
            } else {
                axisalignedbb = this.getBoundingBox().inflate(1.0D, 0.5D, 1.0D);
            }

            List<Entity> list = this.level().getEntities(this, axisalignedbb);
            List<Entity> list1 = Lists.newArrayList();
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (entity.getType() == EntityType.EXPERIENCE_ORB) {
                    list1.add(entity);
                } else if (!entity.isRemoved()) {
                    this.touch(entity);
                }
            }

            if (!list1.isEmpty()) {
                this.touch((Entity) Util.getRandom((List) list1, this.random));
            }
        }

        this.playShoulderEntityAmbientSound(this.getShoulderEntityLeft());
        this.playShoulderEntityAmbientSound(this.getShoulderEntityRight());
        if (!this.level().isClientSide && (this.fallDistance > 0.5F || this.isInWater()) || this.abilities.flying || this.isSleeping() || this.isInPowderSnow) {
            if (!this.level().paperConfig().entities.behavior.parrotsAreUnaffectedByPlayerMovement) // Paper - Add option to make parrots stay
            this.removeEntitiesOnShoulder();
        }

    }

    private void playShoulderEntityAmbientSound(@Nullable CompoundTag entityNbt) {
        if (entityNbt != null && (!entityNbt.contains("Silent") || !entityNbt.getBoolean("Silent")) && this.level().random.nextInt(200) == 0) {
            String s = entityNbt.getString("id");

            EntityType.byString(s).filter((entitytypes) -> {
                return entitytypes == EntityType.PARROT;
            }).ifPresent((entitytypes) -> {
                if (!Parrot.imitateNearbyMobs(this.level(), this)) {
                    this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), Parrot.getAmbient(this.level(), this.level().random), this.getSoundSource(), 1.0F, Parrot.getPitch(this.level().random));
                }

            });
        }

    }

    private void touch(Entity entity) {
        entity.playerTouch(this);
    }

    public int getScore() {
        return (Integer) this.entityData.get(Player.DATA_SCORE_ID);
    }

    public void setScore(int score) {
        this.entityData.set(Player.DATA_SCORE_ID, score);
    }

    public void increaseScore(int score) {
        int j = this.getScore();

        this.entityData.set(Player.DATA_SCORE_ID, j + score);
    }

    public void startAutoSpinAttack(int riptideTicks, float riptideAttackDamage, ItemStack stack) {
        this.autoSpinAttackTicks = riptideTicks;
        this.autoSpinAttackDmg = riptideAttackDamage;
        this.autoSpinAttackItemStack = stack;
        if (!this.level().isClientSide) {
            this.removeEntitiesOnShoulder();
            this.setLivingEntityFlag(4, true);
        }

    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.isAutoSpinAttack() && this.autoSpinAttackItemStack != null ? this.autoSpinAttackItemStack : super.getWeaponItem();
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.reapplyPosition();
        if (!this.isSpectator()) {
            Level world = this.level();

            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                this.dropAllDeathLoot(worldserver, damageSource);
            }
        }

        if (damageSource != null) {
            this.setDeltaMovement((double) (-Mth.cos((this.getHurtDir() + this.getYRot()) * 0.017453292F) * 0.1F), 0.10000000149011612D, (double) (-Mth.sin((this.getHurtDir() + this.getYRot()) * 0.017453292F) * 0.1F));
        } else {
            this.setDeltaMovement(0.0D, 0.1D, 0.0D);
        }

        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlagOnFire(false);
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (!this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            this.destroyVanishingCursedItems();
            this.inventory.dropAll();
        }

    }

    protected void destroyVanishingCursedItems() {
        for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);

            if (!itemstack.isEmpty() && EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                this.inventory.removeItemNoUpdate(i);
            }
        }

    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return source.type().effects().sound();
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Nullable
    public ItemEntity drop(ItemStack stack, boolean retainOwnership) {
        return this.drop(stack, false, retainOwnership);
    }

    @Nullable
    public ItemEntity drop(ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        // CraftBukkit start - SPIGOT-2942: Add boolean to call event
        return this.drop(stack, throwRandomly, retainOwnership, true);
    }

    @Nullable
    public ItemEntity drop(ItemStack itemstack, boolean flag, boolean flag1, boolean callEvent) {
        // CraftBukkit end
        if (itemstack.isEmpty()) {
            return null;
        } else {
            if (this.level().isClientSide) {
                this.swing(InteractionHand.MAIN_HAND);
            }

            double d0 = this.getEyeY() - 0.30000001192092896D;
            // Paper start
            ItemStack tmp = itemstack.copy();
            itemstack.setCount(0);
            itemstack = tmp;
            // Paper end
            ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), d0, this.getZ(), itemstack);

            entityitem.setPickUpDelay(40);
            if (flag1) {
                entityitem.setThrower(this);
            }

            float f;
            float f1;

            if (flag) {
                f = this.random.nextFloat() * 0.5F;
                f1 = this.random.nextFloat() * 6.2831855F;
                entityitem.setDeltaMovement((double) (-Mth.sin(f1) * f), 0.20000000298023224D, (double) (Mth.cos(f1) * f));
            } else {
                f = 0.3F;
                f1 = Mth.sin(this.getXRot() * 0.017453292F);
                float f2 = Mth.cos(this.getXRot() * 0.017453292F);
                float f3 = Mth.sin(this.getYRot() * 0.017453292F);
                float f4 = Mth.cos(this.getYRot() * 0.017453292F);
                float f5 = this.random.nextFloat() * 6.2831855F;
                float f6 = 0.02F * this.random.nextFloat();

                entityitem.setDeltaMovement((double) (-f3 * f2 * 0.3F) + Math.cos((double) f5) * (double) f6, (double) (-f1 * 0.3F + 0.1F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F), (double) (f4 * f2 * 0.3F) + Math.sin((double) f5) * (double) f6);
            }

            // CraftBukkit start - fire PlayerDropItemEvent
            if (!callEvent) { // SPIGOT-2942: Add boolean to call event
                return entityitem;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) this.getBukkitEntity();
            Item drop = (Item) entityitem.getBukkitEntity();

            PlayerDropItemEvent event = new PlayerDropItemEvent(player, drop);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                org.bukkit.inventory.ItemStack cur = player.getInventory().getItemInHand();
                if (flag1 && (cur == null || cur.getAmount() == 0)) {
                    // The complete stack was dropped
                    player.getInventory().setItemInHand(drop.getItemStack());
                } else if (flag1 && cur.isSimilar(drop.getItemStack()) && cur.getAmount() < cur.getMaxStackSize() && drop.getItemStack().getAmount() == 1) {
                    // Only one item is dropped
                    cur.setAmount(cur.getAmount() + 1);
                    player.getInventory().setItemInHand(cur);
                } else {
                    // Fallback
                    player.getInventory().addItem(drop.getItemStack());
                }
                return null;
            }
            // CraftBukkit end

            return entityitem;
        }
    }

    public float getDestroySpeed(BlockState block) {
        float f = this.inventory.getDestroySpeed(block);

        if (f > 1.0F) {
            f += (float) this.getAttributeValue(Attributes.MINING_EFFICIENCY);
        }

        if (MobEffectUtil.hasDigSpeed(this)) {
            f *= 1.0F + (float) (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }

        if (this.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            float f1;

            switch (this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0:
                    f1 = 0.3F;
                    break;
                case 1:
                    f1 = 0.09F;
                    break;
                case 2:
                    f1 = 0.0027F;
                    break;
                case 3:
                default:
                    f1 = 8.1E-4F;
            }

            f *= f1;
        }

        f *= (float) this.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (this.isEyeInFluid(FluidTags.WATER)) {
            f *= (float) this.getAttribute(Attributes.SUBMERGED_MINING_SPEED).getValue();
        }

        if (!this.onGround()) {
            f /= 5.0F;
        }

        return f;
    }

    public boolean hasCorrectToolForDrops(BlockState state) {
        return !state.requiresCorrectToolForDrops() || this.inventory.getSelected().isCorrectToolForDrops(state);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setUUID(this.gameProfile.getId());
        ListTag nbttaglist = nbt.getList("Inventory", 10);

        this.inventory.load(nbttaglist);
        this.inventory.selected = nbt.getInt("SelectedItemSlot");
        this.sleepCounter = nbt.getShort("SleepTimer");
        this.experienceProgress = nbt.getFloat("XpP");
        this.experienceLevel = nbt.getInt("XpLevel");
        this.totalExperience = nbt.getInt("XpTotal");
        this.enchantmentSeed = nbt.getInt("XpSeed");
        if (this.enchantmentSeed == 0) {
            this.enchantmentSeed = this.random.nextInt();
        }

        this.setScore(nbt.getInt("Score"));
        this.foodData.readAdditionalSaveData(nbt);
        this.abilities.loadSaveData(nbt);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double) this.abilities.getWalkingSpeed());
        if (nbt.contains("EnderItems", 9)) {
            this.enderChestInventory.fromTag(nbt.getList("EnderItems", 10), this.registryAccess());
        }

        if (nbt.contains("ShoulderEntityLeft", 10)) {
            this.setShoulderEntityLeft(nbt.getCompound("ShoulderEntityLeft"));
        }

        if (nbt.contains("ShoulderEntityRight", 10)) {
            this.setShoulderEntityRight(nbt.getCompound("ShoulderEntityRight"));
        }

        if (nbt.contains("LastDeathLocation", 10)) {
            DataResult<GlobalPos> dataresult = GlobalPos.CODEC.parse(NbtOps.INSTANCE, nbt.get("LastDeathLocation")); // CraftBukkit - decompile error
            Logger logger = Player.LOGGER;

            Objects.requireNonNull(logger);
            this.setLastDeathLocation(dataresult.resultOrPartial(logger::error));
        }

        if (nbt.contains("current_explosion_impact_pos", 9)) {
            DataResult<Vec3> dataresult1 = Vec3.CODEC.parse(NbtOps.INSTANCE, nbt.get("current_explosion_impact_pos")); // CraftBukkit - decompile error
            Logger logger1 = Player.LOGGER;

            Objects.requireNonNull(logger1);
            dataresult1.resultOrPartial(logger1::error).ifPresent((vec3d) -> {
                this.currentImpulseImpactPos = vec3d;
            });
        }

        this.ignoreFallDamageFromCurrentImpulse = nbt.getBoolean("ignore_fall_damage_from_current_explosion");
        this.currentImpulseContextResetGraceTime = nbt.getInt("current_impulse_context_reset_grace_time");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        NbtUtils.addCurrentDataVersion(nbt);
        nbt.put("Inventory", this.inventory.save(new ListTag()));
        nbt.putInt("SelectedItemSlot", this.inventory.selected);
        nbt.putShort("SleepTimer", (short) this.sleepCounter);
        nbt.putFloat("XpP", this.experienceProgress);
        nbt.putInt("XpLevel", this.experienceLevel);
        nbt.putInt("XpTotal", this.totalExperience);
        nbt.putInt("XpSeed", this.enchantmentSeed);
        nbt.putInt("Score", this.getScore());
        this.foodData.addAdditionalSaveData(nbt);
        this.abilities.addSaveData(nbt);
        nbt.put("EnderItems", this.enderChestInventory.createTag(this.registryAccess()));
        if (!this.getShoulderEntityLeft().isEmpty()) {
            nbt.put("ShoulderEntityLeft", this.getShoulderEntityLeft());
        }

        if (!this.getShoulderEntityRight().isEmpty()) {
            nbt.put("ShoulderEntityRight", this.getShoulderEntityRight());
        }

        this.getLastDeathLocation().flatMap((globalpos) -> {
            DataResult<Tag> dataresult = GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, globalpos); // CraftBukkit - decompile error
            Logger logger = Player.LOGGER;

            Objects.requireNonNull(logger);
            return dataresult.resultOrPartial(logger::error);
        }).ifPresent((nbtbase) -> {
            nbt.put("LastDeathLocation", nbtbase);
        });
        if (this.currentImpulseImpactPos != null) {
            nbt.put("current_explosion_impact_pos", (Tag) Vec3.CODEC.encodeStart(NbtOps.INSTANCE, this.currentImpulseImpactPos).getOrThrow());
        }

        nbt.putBoolean("ignore_fall_damage_from_current_explosion", this.ignoreFallDamageFromCurrentImpulse);
        nbt.putInt("current_impulse_context_reset_grace_time", this.currentImpulseContextResetGraceTime);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return super.isInvulnerableTo(damageSource) ? true : (damageSource.is(DamageTypeTags.IS_DROWNING) ? !this.level().getGameRules().getBoolean(GameRules.RULE_DROWNING_DAMAGE) : (damageSource.is(DamageTypeTags.IS_FALL) ? !this.level().getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE) : (damageSource.is(DamageTypeTags.IS_FIRE) ? !this.level().getGameRules().getBoolean(GameRules.RULE_FIRE_DAMAGE) : (damageSource.is(DamageTypeTags.IS_FREEZING) ? !this.level().getGameRules().getBoolean(GameRules.RULE_FREEZE_DAMAGE) : false))));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (this.abilities.invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            this.noActionTime = 0;
            if (this.isDeadOrDying()) {
                return false;
            } else {
                if (!this.level().isClientSide) {
                    // this.removeEntitiesOnShoulder(); // CraftBukkit - moved down
                }

                if (source.scalesWithDifficulty()) {
                    if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
                        return false; // CraftBukkit - f = 0.0f -> return false
                    }

                    if (this.level().getDifficulty() == Difficulty.EASY) {
                        amount = Math.min(amount / 2.0F + 1.0F, amount);
                    }

                    if (this.level().getDifficulty() == Difficulty.HARD) {
                        amount = amount * 3.0F / 2.0F;
                    }
                }

                // CraftBukkit start - Don't filter out 0 damage
                boolean damaged = super.hurt(source, amount);
                if (damaged) {
                    this.removeEntitiesOnShoulder();
                }
                return damaged;
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        super.blockUsingShield(attacker);
        if (attacker.canDisableShield()) {
            this.disableShield();
        }

    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.getAbilities().invulnerable && super.canBeSeenAsEnemy();
    }

    public boolean canHarmPlayer(Player player) {
        // CraftBukkit start - Change to check OTHER player's scoreboard team according to API
        // To summarize this method's logic, it's "Can parameter hurt this"
        org.bukkit.scoreboard.Team team;
        if (player instanceof ServerPlayer) {
            ServerPlayer thatPlayer = (ServerPlayer) player;
            team = thatPlayer.getBukkitEntity().getScoreboard().getPlayerTeam(thatPlayer.getBukkitEntity());
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        } else {
            // This should never be called, but is implemented anyway
            org.bukkit.OfflinePlayer thisPlayer = player.level().getCraftServer().getOfflinePlayer(player.getScoreboardName());
            team = player.level().getCraftServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(thisPlayer);
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        }

        if (this instanceof ServerPlayer) {
            return !team.hasPlayer(((ServerPlayer) this).getBukkitEntity());
        }
        return !team.hasPlayer(this.level().getCraftServer().getOfflinePlayer(this.getScoreboardName()));
        // CraftBukkit end
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD});
    }

    @Override
    protected void hurtHelmet(DamageSource source, float amount) {
        this.doHurtEquipment(source, amount, new EquipmentSlot[]{EquipmentSlot.HEAD});
    }

    @Override
    protected void hurtCurrentlyUsedShield(float amount) {
        if (this.useItem.is(Items.SHIELD)) {
            if (!this.level().isClientSide) {
                this.awardStat(Stats.ITEM_USED.get(this.useItem.getItem()));
            }

            if (amount >= 3.0F) {
                int i = 1 + Mth.floor(amount);
                InteractionHand enumhand = this.getUsedItemHand();

                this.useItem.hurtAndBreak(i, this, getSlotForHand(enumhand));
                if (this.useItem.isEmpty()) {
                    if (enumhand == InteractionHand.MAIN_HAND) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    } else {
                        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    }

                    this.useItem = ItemStack.EMPTY;
                    this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
                }
            }

        }
    }

    // CraftBukkit start
    @Override
    protected boolean actuallyHurt(DamageSource damagesource, float f, EntityDamageEvent event) { // void -> boolean
        if (true) {
            return super.actuallyHurt(damagesource, f, event);
        }
        // CraftBukkit end
        if (!this.isInvulnerableTo(damagesource)) {
            f = this.getDamageAfterArmorAbsorb(damagesource, f);
            f = this.getDamageAfterMagicAbsorb(damagesource, f);
            float f1 = f;

            f = Math.max(f - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (f1 - f));
            float f2 = f1 - f;

            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                this.awardStat(Stats.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }

            if (f != 0.0F) {
                this.causeFoodExhaustion(damagesource.getFoodExhaustion(), EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                this.getCombatTracker().recordDamage(damagesource, f);
                this.setHealth(this.getHealth() - f);
                if (f < 3.4028235E37F) {
                    this.awardStat(Stats.DAMAGE_TAKEN, Math.round(f * 10.0F));
                }

                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
        return false; // CraftBukkit
    }

    public boolean isTextFilteringEnabled() {
        return false;
    }

    public void openTextEdit(SignBlockEntity sign, boolean front) {}

    public void openMinecartCommandBlock(BaseCommandBlock commandBlockExecutor) {}

    public void openCommandBlock(CommandBlockEntity commandBlock) {}

    public void openStructureBlock(StructureBlockEntity structureBlock) {}

    public void openJigsawBlock(JigsawBlockEntity jigsaw) {}

    public void openHorseInventory(AbstractHorse horse, Container inventory) {}

    public OptionalInt openMenu(@Nullable MenuProvider factory) {
        return OptionalInt.empty();
    }

    public void sendMerchantOffers(int syncId, MerchantOffers offers, int levelProgress, int experience, boolean leveled, boolean refreshable) {}

    public void openItemGui(ItemStack book, InteractionHand hand) {}

    public InteractionResult interactOn(Entity entity, InteractionHand hand) {
        if (this.isSpectator()) {
            if (entity instanceof MenuProvider) {
                this.openMenu((MenuProvider) entity);
            }

            return InteractionResult.PASS;
        } else {
            ItemStack itemstack = this.getItemInHand(hand);
            ItemStack itemstack1 = itemstack.copy();
            InteractionResult enuminteractionresult = entity.interact(this, hand);

            if (enuminteractionresult.consumesAction()) {
                if (this.abilities.instabuild && itemstack == this.getItemInHand(hand) && itemstack.getCount() < itemstack1.getCount()) {
                    itemstack.setCount(itemstack1.getCount());
                }

                return enuminteractionresult;
            } else {
                if (!itemstack.isEmpty() && entity instanceof LivingEntity) {
                    if (this.abilities.instabuild) {
                        itemstack = itemstack1;
                    }

                    InteractionResult enuminteractionresult1 = itemstack.interactLivingEntity(this, (LivingEntity) entity, hand);

                    if (enuminteractionresult1.consumesAction()) {
                        this.level().gameEvent((Holder) GameEvent.ENTITY_INTERACT, entity.position(), GameEvent.Context.of((Entity) this));
                        if (itemstack.isEmpty() && !this.abilities.instabuild) {
                            this.setItemInHand(hand, ItemStack.EMPTY);
                        }

                        return enuminteractionresult1;
                    }
                }

                return InteractionResult.PASS;
            }
        }
    }

    @Override
    public void removeVehicle() {
        // Paper start - Force entity dismount during teleportation
        this.removeVehicle(false);
    }
    @Override
    public void removeVehicle(boolean suppressCancellation) {
        super.removeVehicle(suppressCancellation);
        // Paper end - Force entity dismount during teleportation
        this.boardingCooldown = 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isSleeping() || this.isRemoved() || !valid; // Paper - player's who are dead or not in a world shouldn't move...
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.abilities.flying;
    }

    @Override
    protected Vec3 maybeBackOffFromEdge(Vec3 movement, MoverType type) {
        float f = this.maxUpStep();

        if (!this.abilities.flying && movement.y <= 0.0D && (type == MoverType.SELF || type == MoverType.PLAYER) && this.isStayingOnGroundSurface() && this.isAboveGround(f)) {
            double d0 = movement.x;
            double d1 = movement.z;
            double d2 = 0.05D;
            double d3 = Math.signum(d0) * 0.05D;

            double d4;

            for (d4 = Math.signum(d1) * 0.05D; d0 != 0.0D && this.canFallAtLeast(d0, 0.0D, f); d0 -= d3) {
                if (Math.abs(d0) <= 0.05D) {
                    d0 = 0.0D;
                    break;
                }
            }

            while (d1 != 0.0D && this.canFallAtLeast(0.0D, d1, f)) {
                if (Math.abs(d1) <= 0.05D) {
                    d1 = 0.0D;
                    break;
                }

                d1 -= d4;
            }

            while (d0 != 0.0D && d1 != 0.0D && this.canFallAtLeast(d0, d1, f)) {
                if (Math.abs(d0) <= 0.05D) {
                    d0 = 0.0D;
                } else {
                    d0 -= d3;
                }

                if (Math.abs(d1) <= 0.05D) {
                    d1 = 0.0D;
                } else {
                    d1 -= d4;
                }
            }

            return new Vec3(d0, movement.y, d1);
        } else {
            return movement;
        }
    }

    private boolean isAboveGround(float f) {
        return this.onGround() || this.fallDistance < f && !this.canFallAtLeast(0.0D, 0.0D, f - this.fallDistance);
    }

    private boolean canFallAtLeast(double offsetX, double offsetZ, float f) {
        AABB axisalignedbb = this.getBoundingBox();

        return this.level().noCollision(this, new AABB(axisalignedbb.minX + offsetX, axisalignedbb.minY - (double) f - 9.999999747378752E-6D, axisalignedbb.minZ + offsetZ, axisalignedbb.maxX + offsetX, axisalignedbb.minY, axisalignedbb.maxZ + offsetZ));
    }

    public void attack(Entity target) {
        // Paper start - PlayerAttackEntityEvent
        boolean willAttack = target.isAttackable() && !target.skipAttackInteraction(this); // Vanilla logic
        io.papermc.paper.event.player.PrePlayerAttackEntityEvent playerAttackEntityEvent = new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(
            (org.bukkit.entity.Player) this.getBukkitEntity(),
            target.getBukkitEntity(),
            willAttack
        );

        if (playerAttackEntityEvent.callEvent() && willAttack) { // Logic moved to willAttack local variable.
            {
        // Paper end - PlayerAttackEntityEvent
                float f = this.isAutoSpinAttack() ? this.autoSpinAttackDmg : (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                ItemStack itemstack = this.getWeaponItem();
                DamageSource damagesource = this.damageSources().playerAttack(this);
                float f1 = this.getEnchantedDamage(target, f, damagesource) - f;
                float f2 = this.getAttackStrengthScale(0.5F);

                f *= 0.2F + f2 * f2 * 0.8F;
                f1 *= f2;
                // this.resetAttackStrengthTicker(); // CraftBukkit - Moved to EntityLiving to reset the cooldown after the damage is dealt
                if (target.getType().is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && target instanceof Projectile) {
                    Projectile iprojectile = (Projectile) target;

                    // CraftBukkit start
                    if (CraftEventFactory.handleNonLivingEntityDamageEvent(target, damagesource, f1, false)) {
                        return;
                    }
                    // CraftBukkit end
                    if (iprojectile.deflect(ProjectileDeflection.AIM_DEFLECT, this, this, true)) {
                        this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, this.getSoundSource());
                        return;
                    }
                }

                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = f2 > 0.9F;
                    boolean flag1;

                    if (this.isSprinting() && flag) {
                        sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                        flag1 = true;
                    } else {
                        flag1 = false;
                    }

                    f += itemstack.getItem().getAttackDamageBonus(target, f, damagesource);
                    boolean flag2 = flag && this.fallDistance > 0.0F && !this.onGround() && !this.onClimbable() && !this.isInWater() && !this.hasEffect(MobEffects.BLINDNESS) && !this.isPassenger() && target instanceof LivingEntity && !this.isSprinting();

                    flag2 = flag2 && !this.level().paperConfig().entities.behavior.disablePlayerCrits; // Paper - Toggleable player crits
                    if (flag2) {
                        damagesource = damagesource.critical(true); // Paper start - critical damage API
                        f *= 1.5F;
                    }

                    float f3 = f + f1;
                    boolean flag3 = false;
                    double d0 = (double) (this.walkDist - this.walkDistO);

                    if (flag && !flag2 && !flag1 && this.onGround() && d0 < (double) this.getSpeed()) {
                        ItemStack itemstack1 = this.getItemInHand(InteractionHand.MAIN_HAND);

                        if (itemstack1.getItem() instanceof SwordItem) {
                            flag3 = true;
                        }
                    }

                    float f4 = 0.0F;

                    if (target instanceof LivingEntity) {
                        LivingEntity entityliving = (LivingEntity) target;

                        f4 = entityliving.getHealth();
                    }

                    Vec3 vec3d = target.getDeltaMovement();
                    boolean flag4 = target.hurt(damagesource, f3);

                    if (flag4) {
                        float f5 = this.getKnockback(target, damagesource) + (flag1 ? 1.0F : 0.0F);

                        if (f5 > 0.0F) {
                            if (target instanceof LivingEntity) {
                                LivingEntity entityliving1 = (LivingEntity) target;

                                entityliving1.knockback((double) (f5 * 0.5F), (double) Mth.sin(this.getYRot() * 0.017453292F), (double) (-Mth.cos(this.getYRot() * 0.017453292F)), this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK); // Paper - knockback events
                            } else {
                                target.push((double) (-Mth.sin(this.getYRot() * 0.017453292F) * f5 * 0.5F), 0.1D, (double) (Mth.cos(this.getYRot() * 0.017453292F) * f5 * 0.5F), this); // Paper - Add EntityKnockbackByEntityEvent and EntityPushedByEntityAttackEvent
                            }

                            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
                            // Paper start - Configurable sprint interruption on attack
                            if (!this.level().paperConfig().misc.disableSprintInterruptionOnAttack) {
                            this.setSprinting(false);
                            }
                            // Paper end - Configurable sprint interruption on attack
                        }

                        LivingEntity entityliving2;

                        if (flag3) {
                            float f6 = 1.0F + (float) this.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO) * f;
                            List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D));
                            Iterator iterator = list.iterator();

                            while (iterator.hasNext()) {
                                entityliving2 = (LivingEntity) iterator.next();
                                if (entityliving2 != this && entityliving2 != target && !this.isAlliedTo((Entity) entityliving2) && (!(entityliving2 instanceof ArmorStand) || !((ArmorStand) entityliving2).isMarker()) && this.distanceToSqr((Entity) entityliving2) < 9.0D) {
                                    float f7 = this.getEnchantedDamage(entityliving2, f6, damagesource) * f2;

                                    // CraftBukkit start - Only apply knockback if the damage hits
                                    if (!entityliving2.hurt(this.damageSources().playerAttack(this).sweep().critical(flag2), f7)) { // Paper - add critical damage API
                                        continue;
                                    }
                                    // CraftBukkit end
                                    entityliving2.knockback(0.4000000059604645D, (double) Mth.sin(this.getYRot() * 0.017453292F), (double) (-Mth.cos(this.getYRot() * 0.017453292F)), this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.SWEEP_ATTACK); // CraftBukkit // Paper - knockback events
                                    // entityliving2.hurt(damagesource, f7); // CraftBukkit - moved up
                                    Level world = this.level();

                                    if (world instanceof ServerLevel) {
                                        ServerLevel worldserver = (ServerLevel) world;

                                        EnchantmentHelper.doPostAttackEffects(worldserver, entityliving2, damagesource);
                                    }
                                }
                            }

                            sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                            this.sweepAttack();
                        }

                        if (target instanceof ServerPlayer && target.hurtMarked) {
                            // CraftBukkit start - Add Velocity Event
                            boolean cancelled = false;
                            org.bukkit.entity.Player player = (org.bukkit.entity.Player) target.getBukkitEntity();
                            org.bukkit.util.Vector velocity = CraftVector.toBukkit(vec3d);

                            PlayerVelocityEvent event = new PlayerVelocityEvent(player, velocity.clone());
                            this.level().getCraftServer().getPluginManager().callEvent(event);

                            if (event.isCancelled()) {
                                cancelled = true;
                            } else if (!velocity.equals(event.getVelocity())) {
                                player.setVelocity(event.getVelocity());
                            }

                            if (!cancelled) {
                            ((ServerPlayer) target).connection.send(new ClientboundSetEntityMotionPacket(target));
                            target.hurtMarked = false;
                            target.setDeltaMovement(vec3d);
                            }
                            // CraftBukkit end
                        }

                        if (flag2) {
                            sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                            this.crit(target);
                        }

                        if (!flag2 && !flag3) {
                            if (flag) {
                                sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                            } else {
                                sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                            }
                        }

                        if (f1 > 0.0F) {
                            this.magicCrit(target);
                        }

                        this.setLastHurtMob(target);
                        Object object = target;

                        if (target instanceof EnderDragonPart) {
                            object = ((EnderDragonPart) target).parentMob;
                        }

                        boolean flag5 = false;
                        Level world1 = this.level();

                        if (world1 instanceof ServerLevel) {
                            ServerLevel worldserver1 = (ServerLevel) world1;

                            if (object instanceof LivingEntity) {
                                entityliving2 = (LivingEntity) object;
                                flag5 = itemstack.hurtEnemy(entityliving2, this);
                            }

                            EnchantmentHelper.doPostAttackEffects(worldserver1, target, damagesource);
                        }

                        if (!this.level().isClientSide && !itemstack.isEmpty() && object instanceof LivingEntity) {
                            if (flag5) {
                                itemstack.postHurtEnemy((LivingEntity) object, this);
                            }

                            if (itemstack.isEmpty()) {
                                if (itemstack == this.getMainHandItem()) {
                                    this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                } else {
                                    this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                                }
                            }
                        }

                        if (target instanceof LivingEntity) {
                            float f8 = f4 - ((LivingEntity) target).getHealth();

                            this.awardStat(Stats.DAMAGE_DEALT, Math.round(f8 * 10.0F));
                            if (this.level() instanceof ServerLevel && f8 > 2.0F) {
                                int i = (int) ((double) f8 * 0.5D);

                                ((ServerLevel) this.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5D), target.getZ(), i, 0.1D, 0.0D, 0.1D, 0.2D);
                            }
                        }

                        this.causeFoodExhaustion(this.level().spigotConfig.combatExhaustion, EntityExhaustionEvent.ExhaustionReason.ATTACK); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                    } else {
                        sendSoundEffect(this, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, this.getSoundSource(), 1.0F, 1.0F); // Paper - send while respecting visibility
                        // CraftBukkit start - resync on cancelled event
                        if (this instanceof ServerPlayer) {
                            ((ServerPlayer) this).getBukkitEntity().updateInventory();
                        }
                        // CraftBukkit end
                    }
                }

            }
        }
    }

    protected float getEnchantedDamage(Entity target, float baseDamage, DamageSource damageSource) {
        return baseDamage;
    }

    @Override
    protected void doAutoAttackOnTouch(LivingEntity target) {
        this.attack(target);
    }

    public void disableShield() {
        this.getCooldowns().addCooldown(Items.SHIELD, 100);
        this.stopUsingItem();
        this.level().broadcastEntityEvent(this, (byte) 30);
    }

    public void crit(Entity target) {}

    public void magicCrit(Entity target) {}

    public void sweepAttack() {
        double d0 = (double) (-Mth.sin(this.getYRot() * 0.017453292F));
        double d1 = (double) Mth.cos(this.getYRot() * 0.017453292F);

        if (this.level() instanceof ServerLevel) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + d0, this.getY(0.5D), this.getZ() + d1, 0, d0, 0.0D, d1, 0.0D);
        }

    }

    public void respawn() {}

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        super.remove(entity_removalreason, cause);
        // CraftBukkit end
        this.inventoryMenu.removed(this);
        if (this.containerMenu != null && this.hasContainerOpen()) {
            this.doCloseContainer();
        }

    }

    public boolean isLocalPlayer() {
        return false;
    }

    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public Abilities getAbilities() {
        return this.abilities;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.abilities.instabuild;
    }

    public void updateTutorialInventoryAction(ItemStack cursorStack, ItemStack slotStack, ClickAction clickType) {}

    public boolean hasContainerOpen() {
        return this.containerMenu != this.inventoryMenu;
    }

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos pos) {
        // CraftBukkit start
        return this.startSleepInBed(pos, false);
    }

    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos blockposition, boolean force) {
        // CraftBukkit end
        this.startSleeping(blockposition);
        this.sleepCounter = 0;
        return Either.right(Unit.INSTANCE);
    }

    public void stopSleepInBed(boolean skipSleepTimer, boolean updateSleepingPlayers) {
        super.stopSleeping();
        if (this.level() instanceof ServerLevel && updateSleepingPlayers) {
            ((ServerLevel) this.level()).updateSleepingPlayerList();
        }

        this.sleepCounter = skipSleepTimer ? 0 : 100;
    }

    @Override
    public void stopSleeping() {
        this.stopSleepInBed(true, true);
    }

    public boolean isSleepingLongEnough() {
        return this.isSleeping() && this.sleepCounter >= 100;
    }

    public int getSleepTimer() {
        return this.sleepCounter;
    }

    public void displayClientMessage(Component message, boolean overlay) {}

    public void awardStat(ResourceLocation stat) {
        this.awardStat(Stats.CUSTOM.get(stat));
    }

    public void awardStat(ResourceLocation stat, int amount) {
        this.awardStat(Stats.CUSTOM.get(stat), amount);
    }

    public void awardStat(Stat<?> stat) {
        this.awardStat(stat, 1);
    }

    public void awardStat(Stat<?> stat, int amount) {}

    public void resetStat(Stat<?> stat) {}

    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> ingredients) {}

    public void awardRecipesByKey(List<ResourceLocation> recipes) {}

    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        this.awardStat(Stats.JUMP);
        if (this.isSprinting()) {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpSprintExhaustion, EntityExhaustionEvent.ExhaustionReason.JUMP_SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        } else {
            this.causeFoodExhaustion(this.level().spigotConfig.jumpWalkExhaustion, EntityExhaustionEvent.ExhaustionReason.JUMP); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
        }

    }

    @Override
    public void travel(Vec3 movementInput) {
        double d0;

        if (this.isSwimming() && !this.isPassenger()) {
            d0 = this.getLookAngle().y;
            double d1 = d0 < -0.2D ? 0.085D : 0.06D;

            if (d0 <= 0.0D || this.jumping || !this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() + 1.0D - 0.1D, this.getZ())).getFluidState().isEmpty()) {
                Vec3 vec3d1 = this.getDeltaMovement();

                this.setDeltaMovement(vec3d1.add(0.0D, (d0 - vec3d1.y) * d1, 0.0D));
            }
        }

        if (this.abilities.flying && !this.isPassenger()) {
            d0 = this.getDeltaMovement().y;
            super.travel(movementInput);
            Vec3 vec3d2 = this.getDeltaMovement();

            this.setDeltaMovement(vec3d2.x, d0 * 0.6D, vec3d2.z);
            this.resetFallDistance();
            // CraftBukkit start
            if (this.getSharedFlag(7) && !org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) {
                this.setSharedFlag(7, false);
            }
            // CraftBukkit end
        } else {
            super.travel(movementInput);
        }

    }

    @Override
    public void updateSwimming() {
        if (this.abilities.flying) {
            this.setSwimming(false);
        } else {
            super.updateSwimming();
        }

    }

    protected boolean freeAt(BlockPos pos) {
        return !this.level().getBlockState(pos).isSuffocating(this.level(), pos);
    }

    @Override
    public float getSpeed() {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (this.abilities.mayfly && !this.flyingFallDamage.toBooleanOrElse(false)) { // Paper - flying fall damage
            return false;
        } else {
            if (fallDistance >= 2.0F) {
                this.awardStat(Stats.FALL_ONE_CM, (int) Math.round((double) fallDistance * 100.0D));
            }

            boolean flag;

            if (this.ignoreFallDamageFromCurrentImpulse && this.currentImpulseImpactPos != null) {
                double d0 = this.currentImpulseImpactPos.y;

                this.tryResetCurrentImpulseContext();
                if (d0 < this.getY()) {
                    return false;
                }

                float f2 = Math.min(fallDistance, (float) (d0 - this.getY()));

                flag = super.causeFallDamage(f2, damageMultiplier, damageSource);
            } else {
                flag = super.causeFallDamage(fallDistance, damageMultiplier, damageSource);
            }

            if (flag) {
                this.resetCurrentImpulseContext();
            }

            return flag;
        }
    }

    public boolean tryToStartFallFlying() {
        if (!this.onGround() && !this.isFallFlying() && !this.isInWater() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);

            if (itemstack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemstack)) {
                this.startFallFlying();
                return true;
            }
        }

        return false;
    }

    public void startFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, true).isCancelled()) {
            this.setSharedFlag(7, true);
        } else {
            // SPIGOT-5542: must toggle like below
            this.setSharedFlag(7, true);
            this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    public void stopFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, false).isCancelled()) {
        this.setSharedFlag(7, true);
        this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    @Override
    protected void doWaterSplashEffect() {
        if (!this.isSpectator()) {
            super.doWaterSplashEffect();
        }

    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (this.isInWater()) {
            this.waterSwimSound();
            this.playMuffledStepSound(state);
        } else {
            BlockPos blockposition1 = this.getPrimaryStepSoundBlockPos(pos);

            if (!pos.equals(blockposition1)) {
                BlockState iblockdata1 = this.level().getBlockState(blockposition1);

                if (iblockdata1.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) {
                    this.playCombinationStepSounds(iblockdata1, state);
                } else {
                    super.playStepSound(blockposition1, iblockdata1);
                }
            } else {
                super.playStepSound(pos, state);
            }
        }

    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    @Override
    public boolean killedEntity(ServerLevel world, LivingEntity other) {
        this.awardStat(Stats.ENTITY_KILLED.get(other.getType()));
        return true;
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        if (!this.abilities.flying) {
            super.makeStuckInBlock(state, multiplier);
        }

        this.tryResetCurrentImpulseContext();
    }

    public void giveExperiencePoints(int experience) {
        this.increaseScore(experience);
        this.experienceProgress += (float) experience / (float) this.getXpNeededForNextLevel();
        this.totalExperience = Mth.clamp(this.totalExperience + experience, 0, Integer.MAX_VALUE);

        while (this.experienceProgress < 0.0F) {
            float f = this.experienceProgress * (float) this.getXpNeededForNextLevel();

            if (this.experienceLevel > 0) {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 1.0F + f / (float) this.getXpNeededForNextLevel();
            } else {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 0.0F;
            }
        }

        while (this.experienceProgress >= 1.0F) {
            this.experienceProgress = (this.experienceProgress - 1.0F) * (float) this.getXpNeededForNextLevel();
            this.giveExperienceLevels(1);
            this.experienceProgress /= (float) this.getXpNeededForNextLevel();
        }

    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void onEnchantmentPerformed(ItemStack enchantedItem, int experienceLevels) {
        this.experienceLevel -= experienceLevels;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        this.enchantmentSeed = this.random.nextInt();
    }

    public void giveExperienceLevels(int levels) {
        this.experienceLevel += levels;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        if (levels > 0 && this.experienceLevel % 5 == 0 && (float) this.lastLevelUpTime < (float) this.tickCount - 100.0F) {
            float f = this.experienceLevel > 30 ? 1.0F : (float) this.experienceLevel / 30.0F;

            this.level().playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_LEVELUP, this.getSoundSource(), f * 0.75F, 1.0F);
            this.lastLevelUpTime = this.tickCount;
        }

    }

    public int getXpNeededForNextLevel() {
        return this.experienceLevel >= 30 ? 112 + (this.experienceLevel - 30) * 9 : (this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2); // Paper - diff on change; calculateTotalExperiencePoints
    }
    // Paper start - send while respecting visibility
    private static void sendSoundEffect(Player fromEntity, double x, double y, double z, SoundEvent soundEffect, SoundSource soundCategory, float volume, float pitch) {
        fromEntity.level().playSound(fromEntity, x, y, z, soundEffect, soundCategory, volume, pitch); // This will not send the effect to the entity itself
        if (fromEntity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEffect), soundCategory, x, y, z, volume, pitch, fromEntity.random.nextLong()));
        }
    }
    // Paper end - send while respecting visibility

    // CraftBukkit start
    public void causeFoodExhaustion(float exhaustion) {
        this.causeFoodExhaustion(exhaustion, EntityExhaustionEvent.ExhaustionReason.UNKNOWN);
    }

    public void causeFoodExhaustion(float f, EntityExhaustionEvent.ExhaustionReason reason) {
        // CraftBukkit end
        if (!this.abilities.invulnerable) {
            if (!this.level().isClientSide) {
                // CraftBukkit start
                EntityExhaustionEvent event = CraftEventFactory.callPlayerExhaustionEvent(this, reason, f);
                if (!event.isCancelled()) {
                    this.foodData.addExhaustion(event.getExhaustion());
                }
                // CraftBukkit end
            }

        }
    }

    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.empty();
    }

    public FoodData getFoodData() {
        return this.foodData;
    }

    public boolean canEat(boolean ignoreHunger) {
        return this.abilities.invulnerable || ignoreHunger || this.foodData.needsFood();
    }

    public boolean isHurt() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public boolean mayBuild() {
        return this.abilities.mayBuild;
    }

    public boolean mayUseItemAt(BlockPos pos, Direction facing, ItemStack stack) {
        if (this.abilities.mayBuild) {
            return true;
        } else {
            BlockPos blockposition1 = pos.relative(facing.getOpposite());
            BlockInWorld shapedetectorblock = new BlockInWorld(this.level(), blockposition1, false);

            return stack.canPlaceOnBlockInAdventureMode(shapedetectorblock);
        }
    }

    @Override
    protected int getBaseExperienceReward() {
        if (!this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && !this.isSpectator()) {
            int i = this.experienceLevel * 7;

            return i > 100 ? 100 : i;
        } else {
            return 0;
        }
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return !this.abilities.flying && (!this.onGround() || !this.isDiscrete()) ? Entity.MovementEmission.ALL : Entity.MovementEmission.NONE;
    }

    public void onUpdateAbilities() {}

    @Override
    public Component getName() {
        return Component.literal(this.gameProfile.getName());
    }

    public PlayerEnderChestContainer getEnderChestInventory() {
        return this.enderChestInventory;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.inventory.getSelected() : (slot == EquipmentSlot.OFFHAND ? (ItemStack) this.inventory.offhand.get(0) : (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? (ItemStack) this.inventory.armor.get(slot.getIndex()) : ItemStack.EMPTY));
    }

    @Override
    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // CraftBukkit start
        this.setItemSlot(slot, stack, false);
    }

    @Override
    public void setItemSlot(EquipmentSlot enumitemslot, ItemStack itemstack, boolean silent) {
        // CraftBukkit end
        this.verifyEquippedItem(itemstack);
        if (enumitemslot == EquipmentSlot.MAINHAND) {
            this.onEquipItem(enumitemslot, (ItemStack) this.inventory.items.set(this.inventory.selected, itemstack), itemstack, silent); // CraftBukkit
        } else if (enumitemslot == EquipmentSlot.OFFHAND) {
            this.onEquipItem(enumitemslot, (ItemStack) this.inventory.offhand.set(0, itemstack), itemstack, silent); // CraftBukkit
        } else if (enumitemslot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            this.onEquipItem(enumitemslot, (ItemStack) this.inventory.armor.set(enumitemslot.getIndex(), itemstack), itemstack, silent); // CraftBukkit
        }

    }

    public boolean addItem(ItemStack stack) {
        return this.inventory.add(stack);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return Lists.newArrayList(new ItemStack[]{this.getMainHandItem(), this.getOffhandItem()});
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.inventory.armor;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY;
    }

    public boolean setEntityOnShoulder(CompoundTag entityNbt) {
        if (!this.isPassenger() && this.onGround() && !this.isInWater() && !this.isInPowderSnow) {
            if (this.getShoulderEntityLeft().isEmpty()) {
                this.setShoulderEntityLeft(entityNbt);
                this.timeEntitySatOnShoulder = this.level().getGameTime();
                return true;
            } else if (this.getShoulderEntityRight().isEmpty()) {
                this.setShoulderEntityRight(entityNbt);
                this.timeEntitySatOnShoulder = this.level().getGameTime();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void removeEntitiesOnShoulder() {
        if (this.timeEntitySatOnShoulder + 20L < this.level().getGameTime()) {
            // CraftBukkit start
            if (this.respawnEntityOnShoulder(this.getShoulderEntityLeft())) {
                this.setShoulderEntityLeft(new CompoundTag());
            }
            if (this.respawnEntityOnShoulder(this.getShoulderEntityRight())) {
                this.setShoulderEntityRight(new CompoundTag());
            }
            // CraftBukkit end
        }

    }

    // Paper start
    public Entity releaseLeftShoulderEntity() {
        Entity entity = this.respawnEntityOnShoulder0(this.getShoulderEntityLeft());
        if (entity != null) {
            this.setShoulderEntityLeft(new CompoundTag());
        }
        return entity;
    }

    public Entity releaseRightShoulderEntity() {
        Entity entity = this.respawnEntityOnShoulder0(this.getShoulderEntityRight());
        if (entity != null) {
            this.setShoulderEntityRight(new CompoundTag());
        }
        return entity;
    }
    // Paper - maintain old signature

    private boolean respawnEntityOnShoulder(CompoundTag nbttagcompound) { // CraftBukkit void->boolean
        return this.respawnEntityOnShoulder0(nbttagcompound) != null;
    }

    // Paper - return entity
    private Entity respawnEntityOnShoulder0(CompoundTag nbttagcompound) { // CraftBukkit void->boolean
        if (!this.level().isClientSide && nbttagcompound != null && !nbttagcompound.isEmpty()) {
            return EntityType.create(nbttagcompound, this.level()).map((entity) -> { // CraftBukkit
                if (entity instanceof TamableAnimal) {
                    ((TamableAnimal) entity).setOwnerUUID(this.uuid);
                }

                entity.setPos(this.getX(), this.getY() + 0.699999988079071D, this.getZ());
                boolean addedToWorld = ((ServerLevel) this.level()).addWithUUID(entity, CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY); // CraftBukkit
                return addedToWorld ? entity : null;
            }).orElse(null); // CraftBukkit // Paper - true -> null
        }

        return null; // Paper - return null
    }
    // Paper end

    @Override
    public abstract boolean isSpectator();

    @Override
    public boolean canBeHitByProjectile() {
        return !this.isSpectator() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isSwimming() {
        return !this.abilities.flying && !this.isSpectator() && super.isSwimming();
    }

    public abstract boolean isCreative();

    @Override
    public boolean isPushedByFluid() {
        return !this.abilities.flying;
    }

    public Scoreboard getScoreboard() {
        return this.level().getScoreboard();
    }

    @Override
    public Component getDisplayName() {
        MutableComponent ichatmutablecomponent = PlayerTeam.formatNameForTeam(this.getTeam(), this.getName());

        return this.decorateDisplayNameComponent(ichatmutablecomponent);
    }

    private MutableComponent decorateDisplayNameComponent(MutableComponent component) {
        String s = this.getGameProfile().getName();

        return component.withStyle((chatmodifier) -> {
            return chatmodifier.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + s + " ")).withHoverEvent(this.createHoverEvent()).withInsertion(s);
        });
    }

    @Override
    public String getScoreboardName() {
        return this.getGameProfile().getName();
    }

    @Override
    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.getEntityData().set(Player.DATA_PLAYER_ABSORPTION_ID, absorptionAmount);
    }

    @Override
    public float getAbsorptionAmount() {
        return (Float) this.getEntityData().get(Player.DATA_PLAYER_ABSORPTION_ID);
    }

    public boolean isModelPartShown(PlayerModelPart modelPart) {
        return ((Byte) this.getEntityData().get(Player.DATA_PLAYER_MODE_CUSTOMISATION) & modelPart.getMask()) == modelPart.getMask();
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        if (mappedIndex == 499) {
            return new SlotAccess() {
                @Override
                public ItemStack get() {
                    return Player.this.containerMenu.getCarried();
                }

                @Override
                public boolean set(ItemStack stack) {
                    Player.this.containerMenu.setCarried(stack);
                    return true;
                }
            };
        } else {
            final int j = mappedIndex - 500;

            if (j >= 0 && j < 4) {
                return new SlotAccess() {
                    @Override
                    public ItemStack get() {
                        return Player.this.inventoryMenu.getCraftSlots().getItem(j);
                    }

                    @Override
                    public boolean set(ItemStack stack) {
                        Player.this.inventoryMenu.getCraftSlots().setItem(j, stack);
                        Player.this.inventoryMenu.slotsChanged(Player.this.inventory);
                        return true;
                    }
                };
            } else if (mappedIndex >= 0 && mappedIndex < this.inventory.items.size()) {
                return SlotAccess.forContainer(this.inventory, mappedIndex);
            } else {
                int k = mappedIndex - 200;

                return k >= 0 && k < this.enderChestInventory.getContainerSize() ? SlotAccess.forContainer(this.enderChestInventory, k) : super.getSlot(mappedIndex);
            }
        }
    }

    public boolean isReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean reducedDebugInfo) {
        this.reducedDebugInfo = reducedDebugInfo;
    }

    @Override
    public void setRemainingFireTicks(int fireTicks) {
        super.setRemainingFireTicks(this.abilities.invulnerable ? Math.min(fireTicks, 1) : fireTicks);
    }

    @Override
    public HumanoidArm getMainArm() {
        return (Byte) this.entityData.get(Player.DATA_PLAYER_MAIN_HAND) == 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public void setMainArm(HumanoidArm arm) {
        this.entityData.set(Player.DATA_PLAYER_MAIN_HAND, (byte) (arm == HumanoidArm.LEFT ? 0 : 1));
    }

    public CompoundTag getShoulderEntityLeft() {
        return (CompoundTag) this.entityData.get(Player.DATA_SHOULDER_LEFT);
    }

    public void setShoulderEntityLeft(CompoundTag entityNbt) {
        this.entityData.set(Player.DATA_SHOULDER_LEFT, entityNbt);
    }

    public CompoundTag getShoulderEntityRight() {
        return (CompoundTag) this.entityData.get(Player.DATA_SHOULDER_RIGHT);
    }

    public void setShoulderEntityRight(CompoundTag entityNbt) {
        this.entityData.set(Player.DATA_SHOULDER_RIGHT, entityNbt);
    }

    public float getCurrentItemAttackStrengthDelay() {
        return (float) (1.0D / this.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0D);
    }

    public float getAttackStrengthScale(float baseTime) {
        return Mth.clamp(((float) this.attackStrengthTicker + baseTime) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public void resetAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
    }

    public ItemCooldowns getCooldowns() {
        return this.cooldowns;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return !this.abilities.flying && !this.isFallFlying() ? super.getBlockSpeedFactor() : 1.0F;
    }

    public float getLuck() {
        return (float) this.getAttributeValue(Attributes.LUCK);
    }

    public boolean canUseGameMasterBlocks() {
        return this.abilities.instabuild && this.getPermissionLevel() >= 2;
    }

    @Override
    public boolean canTakeItem(ItemStack stack) {
        EquipmentSlot enumitemslot = this.getEquipmentSlotForItem(stack);

        return this.getItemBySlot(enumitemslot).isEmpty();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return (EntityDimensions) Player.POSES.getOrDefault(pose, Player.STANDING_DIMENSIONS);
    }

    @Override
    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING);
    }

    // Paper start - PlayerReadyArrowEvent
    protected boolean tryReadyArrow(ItemStack bow, ItemStack itemstack) {
        return !(this instanceof ServerPlayer) ||
                new com.destroystokyo.paper.event.player.PlayerReadyArrowEvent(
                    ((ServerPlayer) this).getBukkitEntity(),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(bow),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack)
                ).callEvent();
    }
    // Paper end - PlayerReadyArrowEvent

    @Override
    public ItemStack getProjectile(ItemStack stack) {
        if (!(stack.getItem() instanceof ProjectileWeaponItem)) {
            return ItemStack.EMPTY;
        } else {
            Predicate<ItemStack> predicate = ((ProjectileWeaponItem) stack.getItem()).getSupportedHeldProjectiles().and(item -> tryReadyArrow(stack, item)); // Paper - PlayerReadyArrowEvent
            ItemStack itemstack1 = ProjectileWeaponItem.getHeldProjectile(this, predicate);

            if (!itemstack1.isEmpty()) {
                return itemstack1;
            } else {
                predicate = ((ProjectileWeaponItem) stack.getItem()).getAllSupportedProjectiles().and(item -> tryReadyArrow(stack, item)); // Paper - PlayerReadyArrowEvent

                for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                    ItemStack itemstack2 = this.inventory.getItem(i);

                    if (predicate.test(itemstack2)) {
                        return itemstack2;
                    }
                }

                return this.abilities.instabuild ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
            }
        }
    }

    @Override
    public ItemStack eat(Level world, ItemStack stack, FoodProperties foodComponent) {
        this.getFoodData().eat(stack, foodComponent); // CraftBukkit
        this.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        world.playSound((Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
        if (this instanceof ServerPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger((ServerPlayer) this, stack);
        }

        ItemStack itemstack1 = super.eat(world, stack, foodComponent);
        Optional<ItemStack> optional = foodComponent.usingConvertsTo();

        if (optional.isPresent() && !this.hasInfiniteMaterials()) {
            if (itemstack1.isEmpty()) {
                return ((ItemStack) optional.get()).copy();
            }

            if (!this.level().isClientSide()) {
                this.getInventory().add(((ItemStack) optional.get()).copy());
            }
        }

        return itemstack1;
    }

    @Override
    public Vec3 getRopeHoldPosition(float delta) {
        double d0 = 0.22D * (this.getMainArm() == HumanoidArm.RIGHT ? -1.0D : 1.0D);
        float f1 = Mth.lerp(delta * 0.5F, this.getXRot(), this.xRotO) * 0.017453292F;
        float f2 = Mth.lerp(delta, this.yBodyRotO, this.yBodyRot) * 0.017453292F;
        double d1;

        if (!this.isFallFlying() && !this.isAutoSpinAttack()) {
            if (this.isVisuallySwimming()) {
                return this.getPosition(delta).add((new Vec3(d0, 0.2D, -0.15D)).xRot(-f1).yRot(-f2));
            } else {
                double d2 = this.getBoundingBox().getYsize() - 1.0D;

                d1 = this.isCrouching() ? -0.2D : 0.07D;
                return this.getPosition(delta).add((new Vec3(d0, d2, d1)).yRot(-f2));
            }
        } else {
            Vec3 vec3d = this.getViewVector(delta);
            Vec3 vec3d1 = this.getDeltaMovement();

            d1 = vec3d1.horizontalDistanceSqr();
            double d3 = vec3d.horizontalDistanceSqr();
            float f3;

            if (d1 > 0.0D && d3 > 0.0D) {
                double d4 = (vec3d1.x * vec3d.x + vec3d1.z * vec3d.z) / Math.sqrt(d1 * d3);
                double d5 = vec3d1.x * vec3d.z - vec3d1.z * vec3d.x;

                f3 = (float) (Math.signum(d5) * Math.acos(d4));
            } else {
                f3 = 0.0F;
            }

            return this.getPosition(delta).add((new Vec3(d0, -0.11D, 0.85D)).zRot(-f3).xRot(-f1).yRot(-f2));
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    public boolean isScoping() {
        return this.isUsingItem() && this.getUseItem().is(Items.SPYGLASS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public Optional<GlobalPos> getLastDeathLocation() {
        return this.lastDeathLocation;
    }

    public void setLastDeathLocation(Optional<GlobalPos> lastDeathPos) {
        this.lastDeathLocation = lastDeathPos;
    }

    @Override
    public float getHurtDir() {
        return this.hurtDir;
    }

    @Override
    public void animateHurt(float yaw) {
        super.animateHurt(yaw);
        this.hurtDir = yaw;
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected float getFlyingSpeed() {
        return this.abilities.flying && !this.isPassenger() ? (this.isSprinting() ? this.abilities.getFlyingSpeed() * 2.0F : this.abilities.getFlyingSpeed()) : (this.isSprinting() ? 0.025999999F : 0.02F);
    }

    public double blockInteractionRange() {
        return this.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }

    public double entityInteractionRange() {
        return this.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
    }

    public boolean canInteractWithEntity(Entity entity, double additionalRange) {
        return entity.isRemoved() ? false : this.canInteractWithEntity(entity.getBoundingBox(), additionalRange);
    }

    public boolean canInteractWithEntity(AABB box, double additionalRange) {
        double d1 = this.entityInteractionRange() + additionalRange;

        return box.distanceToSqr(this.getEyePosition()) < d1 * d1;
    }

    public boolean canInteractWithBlock(BlockPos pos, double additionalRange) {
        double d1 = this.blockInteractionRange() + additionalRange;

        return (new AABB(pos)).distanceToSqr(this.getEyePosition()) < d1 * d1;
    }

    public void setIgnoreFallDamageFromCurrentImpulse(boolean ignoreFallDamageFromCurrentExplosion) {
        this.ignoreFallDamageFromCurrentImpulse = ignoreFallDamageFromCurrentExplosion;
        if (ignoreFallDamageFromCurrentExplosion) {
            this.currentImpulseContextResetGraceTime = 40;
        } else {
            this.currentImpulseContextResetGraceTime = 0;
        }

    }

    public boolean isIgnoringFallDamageFromCurrentImpulse() {
        return this.ignoreFallDamageFromCurrentImpulse;
    }

    public void tryResetCurrentImpulseContext() {
        if (this.currentImpulseContextResetGraceTime == 0) {
            this.resetCurrentImpulseContext();
        }

    }

    public void resetCurrentImpulseContext() {
        this.currentImpulseContextResetGraceTime = 0;
        this.currentExplosionCause = null;
        this.currentImpulseImpactPos = null;
        this.ignoreFallDamageFromCurrentImpulse = false;
    }

    public static enum BedSleepingProblem {

        NOT_POSSIBLE_HERE, NOT_POSSIBLE_NOW(Component.translatable("block.minecraft.bed.no_sleep")), TOO_FAR_AWAY(Component.translatable("block.minecraft.bed.too_far_away")), OBSTRUCTED(Component.translatable("block.minecraft.bed.obstructed")), OTHER_PROBLEM, NOT_SAFE(Component.translatable("block.minecraft.bed.not_safe"));

        @Nullable
        private final Component message;

        private BedSleepingProblem() {
            this.message = null;
        }

        private BedSleepingProblem(final Component ichatbasecomponent) {
            this.message = ichatbasecomponent;
        }

        @Nullable
        public Component getMessage() {
            return this.message;
        }
    }
}
