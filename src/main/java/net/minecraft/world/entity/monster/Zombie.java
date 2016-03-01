package net.minecraft.world.entity.monster;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class Zombie extends Monster {

    private static final ResourceLocation SPEED_MODIFIER_BABY_ID = ResourceLocation.withDefaultNamespace("baby");
    private final AttributeModifier babyModifier = new AttributeModifier(Zombie.SPEED_MODIFIER_BABY_ID, this.level().paperConfig().entities.behavior.babyZombieMovementModifier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE); // Paper - Make baby speed configurable
    private static final ResourceLocation REINFORCEMENT_CALLER_CHARGE_ID = ResourceLocation.withDefaultNamespace("reinforcement_caller_charge");
    private static final AttributeModifier ZOMBIE_REINFORCEMENT_CALLEE_CHARGE = new AttributeModifier(ResourceLocation.withDefaultNamespace("reinforcement_callee_charge"), -0.05000000074505806D, AttributeModifier.Operation.ADD_VALUE);
    private static final ResourceLocation LEADER_ZOMBIE_BONUS_ID = ResourceLocation.withDefaultNamespace("leader_zombie_bonus");
    private static final ResourceLocation ZOMBIE_RANDOM_SPAWN_BONUS_ID = ResourceLocation.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPECIAL_TYPE_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DATA_DROWNED_CONVERSION_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    public static final float ZOMBIE_LEADER_CHANCE = 0.05F;
    public static final int REINFORCEMENT_ATTEMPTS = 50;
    public static final int REINFORCEMENT_RANGE_MAX = 40;
    public static final int REINFORCEMENT_RANGE_MIN = 7;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);
    private static final float BREAK_DOOR_CHANCE = 0.1F;
    public static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = (enumdifficulty) -> {
        return enumdifficulty == Difficulty.HARD;
    };
    private final BreakDoorGoal breakDoorGoal;
    private boolean canBreakDoors;
    private int inWaterTime;
    public int conversionTime;
    private int lastTick = MinecraftServer.currentTick; // CraftBukkit - add field

    public Zombie(EntityType<? extends Zombie> type, Level world) {
        super(type, world);
        this.breakDoorGoal = new BreakDoorGoal(this, Zombie.DOOR_BREAKING_PREDICATE);
    }

    public Zombie(Level world) {
        this(EntityType.ZOMBIE, world);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new Zombie.ZombieAttackTurtleEggGoal(this, 1.0D, 3));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0D, true, 4, this::canBreakDoors));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this, new Class[0])).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        if ( this.level().spigotConfig.zombieAggressiveTowardsVillager ) this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false)); // Spigot
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 35.0D).add(Attributes.MOVEMENT_SPEED, 0.23000000417232513D).add(Attributes.ATTACK_DAMAGE, 3.0D).add(Attributes.ARMOR, 2.0D).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Zombie.DATA_BABY_ID, false);
        builder.define(Zombie.DATA_SPECIAL_TYPE_ID, 0);
        builder.define(Zombie.DATA_DROWNED_CONVERSION_ID, false);
    }

    public boolean isUnderWaterConverting() {
        return (Boolean) this.getEntityData().get(Zombie.DATA_DROWNED_CONVERSION_ID);
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean canBreakDoors) {
        if (this.supportsBreakDoorGoal() && GoalUtils.hasGroundPathNavigation(this)) {
            if (this.canBreakDoors != canBreakDoors) {
                this.canBreakDoors = canBreakDoors;
                ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(canBreakDoors);
                if (canBreakDoors) {
                    this.goalSelector.addGoal(1, this.breakDoorGoal);
                } else {
                    this.goalSelector.removeGoal(this.breakDoorGoal);
                }
            }
        } else if (this.canBreakDoors) {
            this.goalSelector.removeGoal(this.breakDoorGoal);
            this.canBreakDoors = false;
        }

    }

    public boolean supportsBreakDoorGoal() {
        return true;
    }

    @Override
    public boolean isBaby() {
        return (Boolean) this.getEntityData().get(Zombie.DATA_BABY_ID);
    }

    @Override
    protected int getBaseExperienceReward() {
        if (this.isBaby()) {
            this.xpReward = (int) ((double) this.xpReward * 2.5D);
        }

        return super.getBaseExperienceReward();
    }

    @Override
    public void setBaby(boolean baby) {
        this.getEntityData().set(Zombie.DATA_BABY_ID, baby);
        if (this.level() != null && !this.level().isClientSide) {
            AttributeInstance attributemodifiable = this.getAttribute(Attributes.MOVEMENT_SPEED);

            attributemodifiable.removeModifier(this.babyModifier.id()); // Paper - Make baby speed configurable
            if (baby) {
                attributemodifiable.addTransientModifier(this.babyModifier); // Paper - Make baby speed configurable
            }
        }

    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (Zombie.DATA_BABY_ID.equals(data)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(data);
    }

    protected boolean convertsInWater() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isUnderWaterConverting()) {
                // CraftBukkit start - Use wall time instead of ticks for conversion
                int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
                this.conversionTime -= elapsedTicks;
                // CraftBukkit end
                if (this.conversionTime < 0) {
                    this.doUnderWaterConversion();
                }
            } else if (this.convertsInWater()) {
                if (this.isEyeInFluid(FluidTags.WATER)) {
                    ++this.inWaterTime;
                    if (this.inWaterTime >= 600) {
                        this.startUnderWaterConversion(300);
                    }
                } else {
                    this.inWaterTime = -1;
                }
            }
        }

        super.tick();
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public void aiStep() {
        if (this.isAlive()) {
            boolean flag = this.isSunSensitive() && this.isSunBurnTick();

            if (flag) {
                ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);

                if (!itemstack.isEmpty()) {
                    if (itemstack.isDamageableItem()) {
                        Item item = itemstack.getItem();

                        itemstack.setDamageValue(itemstack.getDamageValue() + this.random.nextInt(2));
                        if (itemstack.getDamageValue() >= itemstack.getMaxDamage()) {
                            this.onEquippedItemBroken(item, EquipmentSlot.HEAD);
                            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        }
                    }

                    flag = false;
                }

                if (flag) {
                    this.igniteForSeconds(8.0F);
                }
            }
        }

        super.aiStep();
    }

    public void startUnderWaterConversion(int ticksUntilWaterConversion) {
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
        this.conversionTime = ticksUntilWaterConversion;
        this.getEntityData().set(Zombie.DATA_DROWNED_CONVERSION_ID, true);
    }

    protected void doUnderWaterConversion() {
        this.convertToZombieType(EntityType.DROWNED);
        if (!this.isSilent()) {
            this.level().levelEvent((Player) null, 1040, this.blockPosition(), 0);
        }

    }

    protected void convertToZombieType(EntityType<? extends Zombie> entityType) {
        Zombie entityzombie = (Zombie) this.convertTo(entityType, true, EntityTransformEvent.TransformReason.DROWNED, CreatureSpawnEvent.SpawnReason.DROWNED);

        if (entityzombie != null) {
            entityzombie.handleAttributes(entityzombie.level().getCurrentDifficultyAt(entityzombie.blockPosition()).getSpecialMultiplier());
            entityzombie.setCanBreakDoors(entityzombie.supportsBreakDoorGoal() && this.canBreakDoors());
            // CraftBukkit start - SPIGOT-5208: End conversion to stop event spam
        } else {
            ((org.bukkit.entity.Zombie) this.getBukkitEntity()).setConversionTime(-1);
            // CraftBukkit end
        }

    }

    public boolean isSunSensitive() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!super.hurt(source, amount)) {
            return false;
        } else if (!(this.level() instanceof ServerLevel)) {
            return false;
        } else {
            ServerLevel worldserver = (ServerLevel) this.level();
            LivingEntity entityliving = this.getTarget();

            if (entityliving == null && source.getEntity() instanceof LivingEntity) {
                entityliving = (LivingEntity) source.getEntity();
            }

            if (entityliving != null && this.level().getDifficulty() == Difficulty.HARD && (double) this.random.nextFloat() < this.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                int i = Mth.floor(this.getX());
                int j = Mth.floor(this.getY());
                int k = Mth.floor(this.getZ());
                Zombie entityzombie = new Zombie(this.level());

                for (int l = 0; l < 50; ++l) {
                    int i1 = i + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int j1 = j + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int k1 = k + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    BlockPos blockposition = new BlockPos(i1, j1, k1);
                    EntityType<?> entitytypes = entityzombie.getType();

                    if (SpawnPlacements.isSpawnPositionOk(entitytypes, this.level(), blockposition) && SpawnPlacements.checkSpawnRules(entitytypes, worldserver, MobSpawnType.REINFORCEMENT, blockposition, this.level().random)) {
                        entityzombie.setPos((double) i1, (double) j1, (double) k1);
                        if (!this.level().hasNearbyAlivePlayer((double) i1, (double) j1, (double) k1, 7.0D) && this.level().isUnobstructed(entityzombie) && this.level().noCollision((Entity) entityzombie) && !this.level().containsAnyLiquid(entityzombie.getBoundingBox())) {
                            entityzombie.setTarget(entityliving, EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET, true); // CraftBukkit
                            entityzombie.finalizeSpawn(worldserver, this.level().getCurrentDifficultyAt(entityzombie.blockPosition()), MobSpawnType.REINFORCEMENT, (SpawnGroupData) null);
                            worldserver.addFreshEntityWithPassengers(entityzombie, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS); // CraftBukkit
                            AttributeInstance attributemodifiable = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                            AttributeModifier attributemodifier = attributemodifiable.getModifier(Zombie.REINFORCEMENT_CALLER_CHARGE_ID);
                            double d0 = attributemodifier != null ? attributemodifier.amount() : 0.0D;

                            attributemodifiable.removeModifier(Zombie.REINFORCEMENT_CALLER_CHARGE_ID);
                            attributemodifiable.addPermanentModifier(new AttributeModifier(Zombie.REINFORCEMENT_CALLER_CHARGE_ID, d0 - 0.05D, AttributeModifier.Operation.ADD_VALUE));
                            entityzombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(Zombie.ZOMBIE_REINFORCEMENT_CALLEE_CHARGE);
                            break;
                        }
                    }
                }
            }

            return true;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean flag = super.doHurtTarget(target);

        if (flag) {
            float f = this.level().getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();

            if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < f * 0.3F) {
                // CraftBukkit start
                EntityCombustByEntityEvent event = new EntityCombustByEntityEvent(this.getBukkitEntity(), target.getBukkitEntity(), (float) (2 * (int) f)); // PAIL: fixme
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    target.igniteForSeconds(event.getDuration(), false);
                }
                // CraftBukkit end
            }
        }

        return flag;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_STEP;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance localDifficulty) {
        super.populateDefaultEquipmentSlots(random, localDifficulty);
        if (random.nextFloat() < (this.level().getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            int i = random.nextInt(3);

            if (i == 0) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("IsBaby", this.isBaby());
        nbt.putBoolean("CanBreakDoors", this.canBreakDoors());
        nbt.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
        nbt.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setBaby(nbt.getBoolean("IsBaby"));
        this.setCanBreakDoors(nbt.getBoolean("CanBreakDoors"));
        this.inWaterTime = nbt.getInt("InWaterTime");
        if (nbt.contains("DrownedConversionTime", 99) && nbt.getInt("DrownedConversionTime") > -1) {
            this.startUnderWaterConversion(nbt.getInt("DrownedConversionTime"));
        }

    }

    @Override
    public boolean killedEntity(ServerLevel world, LivingEntity other) {
        boolean flag = super.killedEntity(world, other);

        if ((world.getDifficulty() == Difficulty.NORMAL || world.getDifficulty() == Difficulty.HARD) && other instanceof Villager entityvillager) {
            if (world.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) {
                return flag;
            }
            // CraftBukkit start
            flag = Zombie.zombifyVillager(world, entityvillager, this.blockPosition(), this.isSilent(), CreatureSpawnEvent.SpawnReason.INFECTION) == null;
        }

        return flag;
    }

    public static ZombieVillager zombifyVillager(ServerLevel worldserver, Villager entityvillager, net.minecraft.core.BlockPos blockPosition, boolean silent, CreatureSpawnEvent.SpawnReason spawnReason) {
        {
            ZombieVillager entityzombievillager = (ZombieVillager) entityvillager.convertTo(EntityType.ZOMBIE_VILLAGER, false, EntityTransformEvent.TransformReason.INFECTION, spawnReason);
            // CraftBukkit end

            if (entityzombievillager != null) {
                entityzombievillager.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entityzombievillager.blockPosition()), MobSpawnType.CONVERSION, new Zombie.ZombieGroupData(false, true));
                entityzombievillager.setVillagerData(entityvillager.getVillagerData());
                entityzombievillager.setGossips((Tag) entityvillager.getGossips().store(NbtOps.INSTANCE));
                entityzombievillager.setTradeOffers(entityvillager.getOffers().copy());
                entityzombievillager.setVillagerXp(entityvillager.getVillagerXp());
                // CraftBukkit start
                if (!silent) {
                    worldserver.levelEvent((Player) null, 1026, blockPosition, 0);
                }

                // flag = false;
            }

            return entityzombievillager;
        }
        // CraftBukkit end
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? Zombie.BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return stack.is(Items.EGG) && this.isBaby() && this.isPassenger() ? false : super.canHoldItem(stack);
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return stack.is(Items.GLOW_INK_SAC) ? false : super.wantsToPickUp(stack);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        RandomSource randomsource = world.getRandom();
        Object object = super.finalizeSpawn(world, difficulty, spawnReason, entityData);
        float f = difficulty.getSpecialMultiplier();

        this.setCanPickUpLoot(randomsource.nextFloat() < 0.55F * f);
        if (object == null) {
            object = new Zombie.ZombieGroupData(Zombie.getSpawnAsBabyOdds(randomsource), true);
        }

        if (object instanceof Zombie.ZombieGroupData entityzombie_groupdatazombie) {
            if (entityzombie_groupdatazombie.isBaby) {
                this.setBaby(true);
                if (entityzombie_groupdatazombie.canSpawnJockey) {
                    if ((double) randomsource.nextFloat() < 0.05D) {
                        List<Chicken> list = world.getEntitiesOfClass(Chicken.class, this.getBoundingBox().inflate(5.0D, 3.0D, 5.0D), EntitySelector.ENTITY_NOT_BEING_RIDDEN);

                        if (!list.isEmpty()) {
                            Chicken entitychicken = (Chicken) list.get(0);

                            entitychicken.setChickenJockey(true);
                            this.startRiding(entitychicken);
                        }
                    } else if ((double) randomsource.nextFloat() < 0.05D) {
                        Chicken entitychicken1 = (Chicken) EntityType.CHICKEN.create(this.level());

                        if (entitychicken1 != null) {
                            entitychicken1.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            entitychicken1.finalizeSpawn(world, difficulty, MobSpawnType.JOCKEY, (SpawnGroupData) null);
                            entitychicken1.setChickenJockey(true);
                            this.startRiding(entitychicken1);
                            world.addFreshEntity(entitychicken1, CreatureSpawnEvent.SpawnReason.MOUNT); // CraftBukkit
                        }
                    }
                }
            }

            this.setCanBreakDoors(this.supportsBreakDoorGoal() && randomsource.nextFloat() < f * 0.1F);
            this.populateDefaultEquipmentSlots(randomsource, difficulty);
            this.populateDefaultEquipmentEnchantments(world, randomsource, difficulty);
        }

        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            LocalDate localdate = LocalDate.now();
            int i = localdate.get(ChronoField.DAY_OF_MONTH);
            int j = localdate.get(ChronoField.MONTH_OF_YEAR);

            if (j == 10 && i == 31 && randomsource.nextFloat() < 0.25F) {
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(randomsource.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.armorDropChances[EquipmentSlot.HEAD.getIndex()] = 0.0F;
            }
        }

        this.handleAttributes(f);
        return (SpawnGroupData) object;
    }

    public static boolean getSpawnAsBabyOdds(RandomSource random) {
        return random.nextFloat() < 0.05F;
    }

    protected void handleAttributes(float chanceMultiplier) {
        this.randomizeReinforcementsChance();
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addOrReplacePermanentModifier(new AttributeModifier(Zombie.RANDOM_SPAWN_BONUS_ID, this.random.nextDouble() * 0.05000000074505806D, AttributeModifier.Operation.ADD_VALUE));
        double d0 = this.random.nextDouble() * 1.5D * (double) chanceMultiplier;

        if (d0 > 1.0D) {
            this.getAttribute(Attributes.FOLLOW_RANGE).addOrReplacePermanentModifier(new AttributeModifier(Zombie.ZOMBIE_RANDOM_SPAWN_BONUS_ID, d0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (this.random.nextFloat() < chanceMultiplier * 0.05F) {
            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addOrReplacePermanentModifier(new AttributeModifier(Zombie.LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 0.25D + 0.5D, AttributeModifier.Operation.ADD_VALUE));
            this.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(Zombie.LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 3.0D + 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            this.setCanBreakDoors(this.supportsBreakDoorGoal());
        }

    }

    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * 0.10000000149011612D);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(world, source, causedByPlayer);
        Entity entity = source.getEntity();

        if (entity instanceof Creeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                ItemStack itemstack = this.getSkull();

                if (!itemstack.isEmpty()) {
                    entitycreeper.increaseDroppedSkulls();
                    this.spawnAtLocation(itemstack);
                }
            }
        }

    }

    protected ItemStack getSkull() {
        return new ItemStack(Items.ZOMBIE_HEAD);
    }

    private class ZombieAttackTurtleEggGoal extends RemoveBlockGoal {

        ZombieAttackTurtleEggGoal(final PathfinderMob mob, final double speed, final int range) {
            super(Blocks.TURTLE_EGG, mob, speed, range);
        }

        @Override
        public void playDestroyProgressSound(LevelAccessor world, BlockPos pos) {
            world.playSound((Player) null, pos, SoundEvents.ZOMBIE_DESTROY_EGG, SoundSource.HOSTILE, 0.5F, 0.9F + Zombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(Level world, BlockPos pos) {
            world.playSound((Player) null, pos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + world.random.nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() {
            return 1.14D;
        }
    }

    public static class ZombieGroupData implements SpawnGroupData {

        public final boolean isBaby;
        public final boolean canSpawnJockey;

        public ZombieGroupData(boolean baby, boolean tryChickenJockey) {
            this.isBaby = baby;
            this.canSpawnJockey = tryChickenJockey;
        }
    }
}
