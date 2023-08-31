package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityUnleashEvent.UnleashReason;
// CraftBukkit end

public abstract class Mob extends LivingEntity implements EquipmentUser, Leashable, Targeting {

    private static final EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID = SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BYTE);
    private static final int MOB_FLAG_NO_AI = 1;
    private static final int MOB_FLAG_LEFTHANDED = 2;
    private static final int MOB_FLAG_AGGRESSIVE = 4;
    protected static final int PICKUP_REACH = 1;
    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 0, 1);
    public static final float MAX_WEARING_ARMOR_CHANCE = 0.15F;
    public static final float MAX_PICKUP_LOOT_CHANCE = 0.55F;
    public static final float MAX_ENCHANTED_ARMOR_CHANCE = 0.5F;
    public static final float MAX_ENCHANTED_WEAPON_CHANCE = 0.25F;
    public static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
    public static final float PRESERVE_ITEM_DROP_CHANCE_THRESHOLD = 1.0F;
    public static final int PRESERVE_ITEM_DROP_CHANCE = 2;
    public static final int UPDATE_GOAL_SELECTOR_EVERY_N_TICKS = 2;
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.0399999618530273D) - 0.6000000238418579D;
    protected static final ResourceLocation RANDOM_SPAWN_BONUS_ID = ResourceLocation.withDefaultNamespace("random_spawn_bonus");
    public int ambientSoundTime;
    protected int xpReward;
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private final BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    public GoalSelector goalSelector;
    @Nullable public net.minecraft.world.entity.ai.goal.FloatGoal goalFloat; // Paper - Allow nerfed mobs to jump and float
    public GoalSelector targetSelector;
    @Nullable
    private LivingEntity target;
    private final Sensing sensing;
    private final NonNullList<ItemStack> handItems;
    public final float[] handDropChances;
    private final NonNullList<ItemStack> armorItems;
    public final float[] armorDropChances;
    private ItemStack bodyArmorItem;
    protected float bodyArmorDropChance;
    private boolean canPickUpLoot;
    private boolean persistenceRequired;
    private final Map<PathType, Float> pathfindingMalus;
    @Nullable
    public ResourceKey<LootTable> lootTable;
    public long lootTableSeed;
    @Nullable
    private Leashable.LeashData leashData;
    private BlockPos restrictCenter;
    private float restrictRadius;

    public boolean aware = true; // CraftBukkit

    protected Mob(EntityType<? extends Mob> type, Level world) {
        super(type, world);
        this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
        this.handDropChances = new float[2];
        this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
        this.armorDropChances = new float[4];
        this.bodyArmorItem = ItemStack.EMPTY;
        this.pathfindingMalus = Maps.newEnumMap(PathType.class);
        this.restrictCenter = BlockPos.ZERO;
        this.restrictRadius = -1.0F;
        this.goalSelector = new GoalSelector(world.getProfilerSupplier());
        this.targetSelector = new GoalSelector(world.getProfilerSupplier());
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(world);
        this.sensing = new Sensing(this);
        Arrays.fill(this.armorDropChances, 0.085F);
        Arrays.fill(this.handDropChances, 0.085F);
        this.bodyArmorDropChance = 0.085F;
        if (world != null && !world.isClientSide) {
            this.registerGoals();
        }

    }

    // CraftBukkit start
    public void setPersistenceRequired(boolean persistenceRequired) {
        this.persistenceRequired = persistenceRequired;
    }
    // CraftBukkit end

    protected void registerGoals() {}

    public static AttributeSupplier.Builder createMobAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    protected PathNavigation createNavigation(Level world) {
        return new GroundPathNavigation(this, world);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(PathType nodeType) {
        Mob entityinsentient;
        label17:
        {
            Entity entity = this.getControlledVehicle();

            if (entity instanceof Mob entityinsentient1) {
                if (entityinsentient1.shouldPassengersInheritMalus()) {
                    entityinsentient = entityinsentient1;
                    break label17;
                }
            }

            entityinsentient = this;
        }

        Float ofloat = (Float) entityinsentient.pathfindingMalus.get(nodeType);

        return ofloat == null ? nodeType.getMalus() : ofloat;
    }

    public void setPathfindingMalus(PathType nodeType, float penalty) {
        this.pathfindingMalus.put(nodeType, penalty);
    }

    public void onPathfindingStart() {}

    public void onPathfindingDone() {}

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    public MoveControl getMoveControl() {
        Entity entity = this.getControlledVehicle();

        if (entity instanceof Mob entityinsentient) {
            return entityinsentient.getMoveControl();
        } else {
            return this.moveControl;
        }
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        Entity entity = this.getControlledVehicle();

        if (entity instanceof Mob entityinsentient) {
            return entityinsentient.getNavigation();
        } else {
            return this.navigation;
        }
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        Mob entityinsentient;

        if (!this.isNoAi() && entity instanceof Mob entityinsentient1) {
            if (entity.canControlVehicle()) {
                entityinsentient = entityinsentient1;
                return entityinsentient;
            }
        }

        entityinsentient = null;
        return entityinsentient;
    }

    public Sensing getSensing() {
        return this.sensing;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.target;
    }

    @Nullable
    protected final LivingEntity getTargetFromBrain() {
        return (LivingEntity) this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null); // CraftBukkit - decompile error
    }

    public void setTarget(@Nullable LivingEntity target) {
        // CraftBukkit start - fire event
        this.setTarget(target, EntityTargetEvent.TargetReason.UNKNOWN, true);
    }

    public boolean setTarget(LivingEntity entityliving, EntityTargetEvent.TargetReason reason, boolean fireEvent) {
        if (this.getTarget() == entityliving) return false;
        if (fireEvent) {
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN && this.getTarget() != null && entityliving == null) {
                reason = this.getTarget().isAlive() ? EntityTargetEvent.TargetReason.FORGOT_TARGET : EntityTargetEvent.TargetReason.TARGET_DIED;
            }
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN) {
                this.level().getCraftServer().getLogger().log(java.util.logging.Level.WARNING, "Unknown target reason, please report on the issue tracker", new Exception());
            }
            CraftLivingEntity ctarget = null;
            if (entityliving != null) {
                ctarget = (CraftLivingEntity) entityliving.getBukkitEntity();
            }
            EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(this.getBukkitEntity(), ctarget, reason);
            this.level().getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }

            if (event.getTarget() != null) {
                entityliving = ((CraftLivingEntity) event.getTarget()).getHandle();
            } else {
                entityliving = null;
            }
        }
        this.target = entityliving;
        return true;
        // CraftBukkit end
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.GHAST;
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return false;
    }

    public void ate() {
        this.gameEvent(GameEvent.EAT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Mob.DATA_MOB_FLAGS_ID, (byte) 0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        this.makeSound(this.getAmbientSound());
    }

    @Override
    public void baseTick() {
        super.baseTick();
        this.level().getProfiler().push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        this.level().getProfiler().pop();
    }

    @Override
    protected void playHurtSound(DamageSource damageSource) {
        this.resetAmbientSoundTime();
        super.playHurtSound(damageSource);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getBaseExperienceReward() {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            int j;

            for (j = 0; j < this.armorItems.size(); ++j) {
                if (!((ItemStack) this.armorItems.get(j)).isEmpty() && this.armorDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            for (j = 0; j < this.handItems.size(); ++j) {
                if (!((ItemStack) this.handItems.get(j)).isEmpty() && this.handDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            if (!this.bodyArmorItem.isEmpty() && this.bodyArmorDropChance <= 1.0F) {
                i += 1 + this.random.nextInt(3);
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level().isClientSide) {
            for (int i = 0; i < 20; ++i) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;
                double d3 = 10.0D;

                this.level().addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * 10.0D, this.getRandomY() - d1 * 10.0D, this.getRandomZ(1.0D) - d2 * 10.0D, d0, d1, d2);
            }
        } else {
            this.level().broadcastEntityEvent(this, (byte) 20);
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 20) {
            this.spawnAnim();
        } else {
            super.handleEntityEvent(status);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 5 == 0) {
            this.updateControlFlags();
        }

    }

    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof Mob);
        boolean flag1 = !(this.getVehicle() instanceof Boat);

        this.goalSelector.setControlFlag(Goal.Flag.MOVE, flag);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, flag);
    }

    @Override
    protected float tickHeadTurn(float bodyRotation, float headRotation) {
        this.bodyRotationControl.clientTick();
        return headRotation;
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    // CraftBukkit start - Add delegate method
    public SoundEvent getAmbientSound0() {
        return this.getAmbientSound();
    }
    // CraftBukkit end

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        nbt.putBoolean("PersistenceRequired", this.persistenceRequired);
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.armorItems.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            if (!itemstack.isEmpty()) {
                nbttaglist.add(itemstack.save(this.registryAccess()));
            } else {
                nbttaglist.add(new CompoundTag());
            }
        }

        nbt.put("ArmorItems", nbttaglist);
        ListTag nbttaglist1 = new ListTag();
        float[] afloat = this.armorDropChances;
        int i = afloat.length;

        for (int j = 0; j < i; ++j) {
            float f = afloat[j];

            nbttaglist1.add(FloatTag.valueOf(f));
        }

        nbt.put("ArmorDropChances", nbttaglist1);
        ListTag nbttaglist2 = new ListTag();
        Iterator iterator1 = this.handItems.iterator();

        while (iterator1.hasNext()) {
            ItemStack itemstack1 = (ItemStack) iterator1.next();

            if (!itemstack1.isEmpty()) {
                nbttaglist2.add(itemstack1.save(this.registryAccess()));
            } else {
                nbttaglist2.add(new CompoundTag());
            }
        }

        nbt.put("HandItems", nbttaglist2);
        ListTag nbttaglist3 = new ListTag();
        float[] afloat1 = this.handDropChances;
        int k = afloat1.length;

        for (int l = 0; l < k; ++l) {
            float f1 = afloat1[l];

            nbttaglist3.add(FloatTag.valueOf(f1));
        }

        nbt.put("HandDropChances", nbttaglist3);
        if (!this.bodyArmorItem.isEmpty()) {
            nbt.put("body_armor_item", this.bodyArmorItem.save(this.registryAccess()));
            nbt.putFloat("body_armor_drop_chance", this.bodyArmorDropChance);
        }

        this.writeLeashData(nbt, this.leashData);
        nbt.putBoolean("LeftHanded", this.isLeftHanded());
        if (this.lootTable != null) {
            nbt.putString("DeathLootTable", this.lootTable.location().toString());
            if (this.lootTableSeed != 0L) {
                nbt.putLong("DeathLootTableSeed", this.lootTableSeed);
            }
        }

        if (this.isNoAi()) {
            nbt.putBoolean("NoAI", this.isNoAi());
        }

        nbt.putBoolean("Bukkit.Aware", this.aware); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        // CraftBukkit start - If looting or persistence is false only use it if it was set after we started using it
        if (nbt.contains("CanPickUpLoot", 1)) {
            boolean data = nbt.getBoolean("CanPickUpLoot");
            if (isLevelAtLeast(nbt, 1) || data) {
                this.setCanPickUpLoot(data);
            }
        }

        boolean data = nbt.getBoolean("PersistenceRequired");
        if (isLevelAtLeast(nbt, 1) || data) {
            this.persistenceRequired = data;
        }
        // CraftBukkit end
        ListTag nbttaglist;
        CompoundTag nbttagcompound1;
        int i;

        if (nbt.contains("ArmorItems", 9)) {
            nbttaglist = nbt.getList("ArmorItems", 10);

            for (i = 0; i < this.armorItems.size(); ++i) {
                nbttagcompound1 = nbttaglist.getCompound(i);
                this.armorItems.set(i, ItemStack.parseOptional(this.registryAccess(), nbttagcompound1));
            }
        }

        if (nbt.contains("ArmorDropChances", 9)) {
            nbttaglist = nbt.getList("ArmorDropChances", 5);

            for (i = 0; i < nbttaglist.size(); ++i) {
                this.armorDropChances[i] = nbttaglist.getFloat(i);
            }
        }

        if (nbt.contains("HandItems", 9)) {
            nbttaglist = nbt.getList("HandItems", 10);

            for (i = 0; i < this.handItems.size(); ++i) {
                nbttagcompound1 = nbttaglist.getCompound(i);
                this.handItems.set(i, ItemStack.parseOptional(this.registryAccess(), nbttagcompound1));
            }
        }

        if (nbt.contains("HandDropChances", 9)) {
            nbttaglist = nbt.getList("HandDropChances", 5);

            for (i = 0; i < nbttaglist.size(); ++i) {
                this.handDropChances[i] = nbttaglist.getFloat(i);
            }
        }

        if (nbt.contains("body_armor_item", 10)) {
            this.bodyArmorItem = (ItemStack) ItemStack.parse(this.registryAccess(), nbt.getCompound("body_armor_item")).orElse(ItemStack.EMPTY);
            this.bodyArmorDropChance = nbt.getFloat("body_armor_drop_chance");
        } else {
            this.bodyArmorItem = ItemStack.EMPTY;
        }

        this.leashData = this.readLeashData(nbt);
        this.setLeftHanded(nbt.getBoolean("LeftHanded"));
        if (nbt.contains("DeathLootTable", 8)) {
            this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(nbt.getString("DeathLootTable")));
            this.lootTableSeed = nbt.getLong("DeathLootTableSeed");
        }

        this.setNoAi(nbt.getBoolean("NoAI"));
        // CraftBukkit start
        if (nbt.contains("Bukkit.Aware")) {
            this.aware = nbt.getBoolean("Bukkit.Aware");
        }
        // CraftBukkit end
    }

    @Override
    protected void dropFromLootTable(DamageSource damageSource, boolean causedByPlayer) {
        super.dropFromLootTable(damageSource, causedByPlayer);
        this.lootTable = null;
    }

    @Override
    public final ResourceKey<LootTable> getLootTable() {
        return this.lootTable == null ? this.getDefaultLootTable() : this.lootTable;
    }

    protected ResourceKey<LootTable> getDefaultLootTable() {
        return super.getLootTable();
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    public void setZza(float forwardSpeed) {
        this.zza = forwardSpeed;
    }

    public void setYya(float upwardSpeed) {
        this.yya = upwardSpeed;
    }

    public void setXxa(float sidewaysSpeed) {
        this.xxa = sidewaysSpeed;
    }

    @Override
    public void setSpeed(float movementSpeed) {
        super.setSpeed(movementSpeed);
        this.setZza(movementSpeed);
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.level().getProfiler().push("looting");
        if (!this.level().isClientSide && this.canPickUpLoot() && this.isAlive() && !this.dead && this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            Vec3i baseblockposition = this.getPickupReach();
            List<ItemEntity> list = this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate((double) baseblockposition.getX(), (double) baseblockposition.getY(), (double) baseblockposition.getZ()));
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemEntity entityitem = (ItemEntity) iterator.next();

                if (!entityitem.isRemoved() && !entityitem.getItem().isEmpty() && !entityitem.hasPickUpDelay() && this.wantsToPickUp(entityitem.getItem())) {
                    // Paper start - Item#canEntityPickup
                    if (!entityitem.canMobPickup) {
                        continue;
                    }
                    // Paper end - Item#canEntityPickup
                    this.pickUpItem(entityitem);
                }
            }
        }

        this.level().getProfiler().pop();
    }

    protected Vec3i getPickupReach() {
        return Mob.ITEM_PICKUP_REACH;
    }

    protected void pickUpItem(ItemEntity item) {
        ItemStack itemstack = item.getItem();
        ItemStack itemstack1 = this.equipItemIfPossible(itemstack.copy(), item); // CraftBukkit - add item

        if (!itemstack1.isEmpty()) {
            this.onItemPickup(item);
            this.take(item, itemstack1.getCount());
            itemstack.shrink(itemstack1.getCount());
            if (itemstack.isEmpty()) {
                item.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            }
        }

    }

    public ItemStack equipItemIfPossible(ItemStack stack) {
        // CraftBukkit start - add item
        return this.equipItemIfPossible(stack, null);
    }

    public ItemStack equipItemIfPossible(ItemStack itemstack, ItemEntity entityitem) {
        // CraftBukkit end
        EquipmentSlot enumitemslot = this.getEquipmentSlotForItem(itemstack);
        ItemStack itemstack1 = this.getItemBySlot(enumitemslot);
        boolean flag = this.canReplaceCurrentItem(itemstack, itemstack1);

        if (enumitemslot.isArmor() && !flag) {
            enumitemslot = EquipmentSlot.MAINHAND;
            itemstack1 = this.getItemBySlot(enumitemslot);
            flag = itemstack1.isEmpty();
        }

        // CraftBukkit start
        boolean canPickup = flag && this.canHoldItem(itemstack);
        if (entityitem != null) {
            canPickup = !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, !canPickup).isCancelled();
        }
        if (canPickup) {
            // CraftBukkit end
            double d0 = (double) this.getEquipmentDropChance(enumitemslot);

            if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                this.forceDrops = true; // CraftBukkit
                this.spawnAtLocation(itemstack1);
                this.forceDrops = false; // CraftBukkit
            }

            ItemStack itemstack2 = enumitemslot.limit(itemstack);

            this.setItemSlotAndDropWhenKilled(enumitemslot, itemstack2);
            return itemstack2;
        } else {
            return ItemStack.EMPTY;
        }
    }

    protected void setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack stack) {
        this.setItemSlot(slot, stack);
        this.setGuaranteedDrop(slot);
        this.persistenceRequired = true;
    }

    public void setGuaranteedDrop(EquipmentSlot slot) {
        switch (slot.getType()) {
            case HAND:
                this.handDropChances[slot.getIndex()] = 2.0F;
                break;
            case HUMANOID_ARMOR:
                this.armorDropChances[slot.getIndex()] = 2.0F;
                break;
            case ANIMAL_ARMOR:
                this.bodyArmorDropChance = 2.0F;
        }

    }

    protected boolean canReplaceCurrentItem(ItemStack newStack, ItemStack oldStack) {
        if (oldStack.isEmpty()) {
            return true;
        } else {
            double d0;
            double d1;

            if (newStack.getItem() instanceof SwordItem) {
                if (!(oldStack.getItem() instanceof SwordItem)) {
                    return true;
                } else {
                    d0 = this.getApproximateAttackDamageWithItem(newStack);
                    d1 = this.getApproximateAttackDamageWithItem(oldStack);
                    return d0 != d1 ? d0 > d1 : this.canReplaceEqualItem(newStack, oldStack);
                }
            } else if (newStack.getItem() instanceof BowItem && oldStack.getItem() instanceof BowItem) {
                return this.canReplaceEqualItem(newStack, oldStack);
            } else if (newStack.getItem() instanceof CrossbowItem && oldStack.getItem() instanceof CrossbowItem) {
                return this.canReplaceEqualItem(newStack, oldStack);
            } else {
                Item item = newStack.getItem();

                if (item instanceof ArmorItem) {
                    ArmorItem itemarmor = (ArmorItem) item;

                    if (EnchantmentHelper.has(oldStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                        return false;
                    } else if (!(oldStack.getItem() instanceof ArmorItem)) {
                        return true;
                    } else {
                        ArmorItem itemarmor1 = (ArmorItem) oldStack.getItem();

                        return itemarmor.getDefense() != itemarmor1.getDefense() ? itemarmor.getDefense() > itemarmor1.getDefense() : (itemarmor.getToughness() != itemarmor1.getToughness() ? itemarmor.getToughness() > itemarmor1.getToughness() : this.canReplaceEqualItem(newStack, oldStack));
                    }
                } else {
                    if (newStack.getItem() instanceof DiggerItem) {
                        if (oldStack.getItem() instanceof BlockItem) {
                            return true;
                        }

                        if (oldStack.getItem() instanceof DiggerItem) {
                            d0 = this.getApproximateAttackDamageWithItem(newStack);
                            d1 = this.getApproximateAttackDamageWithItem(oldStack);
                            if (d0 != d1) {
                                return d0 > d1;
                            }

                            return this.canReplaceEqualItem(newStack, oldStack);
                        }
                    }

                    return false;
                }
            }
        }
    }

    private double getApproximateAttackDamageWithItem(ItemStack stack) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        return itemattributemodifiers.compute(this.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
    }

    public boolean canReplaceEqualItem(ItemStack newStack, ItemStack oldStack) {
        return newStack.getDamageValue() < oldStack.getDamageValue() ? true : Mob.hasAnyComponentExceptDamage(newStack) && !Mob.hasAnyComponentExceptDamage(oldStack);
    }

    private static boolean hasAnyComponentExceptDamage(ItemStack stack) {
        DataComponentMap datacomponentmap = stack.getComponents();
        int i = datacomponentmap.size();

        return i > 1 || i == 1 && !datacomponentmap.has(DataComponents.DAMAGE);
    }

    public boolean canHoldItem(ItemStack stack) {
        return true;
    }

    public boolean wantsToPickUp(ItemStack stack) {
        return this.canHoldItem(stack);
    }

    public boolean removeWhenFarAway(double distanceSquared) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Player entityhuman = this.level().findNearbyPlayer(this, -1.0D, EntitySelector.PLAYER_AFFECTS_SPAWNING); // Paper - Affects Spawning API

            if (entityhuman != null) {
                // Paper start - Configurable despawn distances
                final io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DespawnRangePair despawnRangePair = this.level().paperConfig().entities.spawning.despawnRanges.get(this.getType().getCategory());
                final io.papermc.paper.configuration.type.DespawnRange.Shape shape = this.level().paperConfig().entities.spawning.despawnRangeShape;
                final double dy = Math.abs(entityhuman.getY() - this.getY());
                final double dySqr = Math.pow(dy, 2);
                final double dxSqr = Math.pow(entityhuman.getX() - this.getX(), 2);
                final double dzSqr = Math.pow(entityhuman.getZ() - this.getZ(), 2);
                final double distanceSquared = dxSqr + dzSqr + dySqr;
                // Despawn if hard/soft limit is exceeded
                if (despawnRangePair.hard().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy) && this.removeWhenFarAway(distanceSquared)) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                }
                if (despawnRangePair.soft().shouldDespawn(shape, dxSqr, dySqr, dzSqr, dy)) {
                    if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && this.removeWhenFarAway(distanceSquared)) {
                        this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                    }
                } else {
                // Paper end - Configurable despawn distances
                    this.noActionTime = 0;
                }
            }

        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        ++this.noActionTime;
        // Paper start - Allow nerfed mobs to jump and float
        if (!this.aware) {
            if (goalFloat != null) {
                if (goalFloat.canUse()) goalFloat.tick();
                this.getJumpControl().tick();
            }
            return;
        }
        // Paper end - Allow nerfed mobs to jump and float
        ProfilerFiller gameprofilerfiller = this.level().getProfiler();

        gameprofilerfiller.push("sensing");
        this.sensing.tick();
        gameprofilerfiller.pop();
        int i = this.tickCount + this.getId();

        if (i % 2 != 0 && this.tickCount > 1) {
            gameprofilerfiller.push("targetSelector");
            this.targetSelector.tickRunningGoals(false);
            gameprofilerfiller.pop();
            gameprofilerfiller.push("goalSelector");
            this.goalSelector.tickRunningGoals(false);
            gameprofilerfiller.pop();
        } else {
            gameprofilerfiller.push("targetSelector");
            this.targetSelector.tick();
            gameprofilerfiller.pop();
            gameprofilerfiller.push("goalSelector");
            this.goalSelector.tick();
            gameprofilerfiller.pop();
        }

        gameprofilerfiller.push("navigation");
        this.navigation.tick();
        gameprofilerfiller.pop();
        gameprofilerfiller.push("mob tick");
        this.customServerAiStep();
        gameprofilerfiller.pop();
        gameprofilerfiller.push("controls");
        gameprofilerfiller.push("move");
        this.moveControl.tick();
        gameprofilerfiller.popPush("look");
        this.lookControl.tick();
        gameprofilerfiller.popPush("jump");
        this.jumpControl.tick();
        gameprofilerfiller.pop();
        gameprofilerfiller.pop();
        this.sendDebugPackets();
    }

    protected void sendDebugPackets() {
        DebugPackets.sendGoalSelector(this.level(), this, this.goalSelector);
    }

    protected void customServerAiStep() {}

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    protected void clampHeadRotationToBody() {
        float f = (float) this.getMaxHeadYRot();
        float f1 = this.getYHeadRot();
        float f2 = Mth.wrapDegrees(this.yBodyRot - f1);
        float f3 = Mth.clamp(Mth.wrapDegrees(this.yBodyRot - f1), -f, f);
        float f4 = f1 + f2 - f3;

        this.setYHeadRot(f4);
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    public void lookAt(Entity targetEntity, float maxYawChange, float maxPitchChange) {
        double d0 = targetEntity.getX() - this.getX();
        double d1 = targetEntity.getZ() - this.getZ();
        double d2;

        if (targetEntity instanceof LivingEntity entityliving) {
            d2 = entityliving.getEyeY() - this.getEyeY();
        } else {
            d2 = (targetEntity.getBoundingBox().minY + targetEntity.getBoundingBox().maxY) / 2.0D - this.getEyeY();
        }

        double d3 = Math.sqrt(d0 * d0 + d1 * d1);
        float f2 = (float) (Mth.atan2(d1, d0) * 57.2957763671875D) - 90.0F;
        float f3 = (float) (-(Mth.atan2(d2, d3) * 57.2957763671875D));

        this.setXRot(this.rotlerp(this.getXRot(), f3, maxPitchChange));
        this.setYRot(this.rotlerp(this.getYRot(), f2, maxYawChange));
    }

    private float rotlerp(float from, float to, float max) {
        float f3 = Mth.wrapDegrees(to - from);

        if (f3 > max) {
            f3 = max;
        }

        if (f3 < -max) {
            f3 = -max;
        }

        return from + f3;
    }

    public static boolean checkMobSpawnRules(EntityType<? extends Mob> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        BlockPos blockposition1 = pos.below();

        return spawnReason == MobSpawnType.SPAWNER || world.getBlockState(blockposition1).isValidSpawn(world, blockposition1, type);
    }

    public boolean checkSpawnRules(LevelAccessor world, MobSpawnType spawnReason) {
        return true;
    }

    public boolean checkSpawnObstruction(LevelReader world) {
        return !world.containsAnyLiquid(this.getBoundingBox()) && world.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int count) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return this.getComfortableFallDistance(0.0F);
        } else {
            int i = (int) (this.getHealth() - this.getMaxHealth() * 0.33F);

            i -= (3 - this.level().getDifficulty().getId()) * 4;
            if (i < 0) {
                i = 0;
            }

            return this.getComfortableFallDistance((float) i);
        }
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return this.handItems;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    public ItemStack getBodyArmorItem() {
        return this.bodyArmorItem;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY;
    }

    public boolean isWearingBodyArmor() {
        return !this.getItemBySlot(EquipmentSlot.BODY).isEmpty();
    }

    public boolean isBodyArmorItem(ItemStack stack) {
        return false;
    }

    public void setBodyArmorItem(ItemStack stack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, stack);
    }

    @Override
    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return (Iterable) (this.bodyArmorItem.isEmpty() ? this.armorItems : Iterables.concat(this.armorItems, List.of(this.bodyArmorItem)));
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        ItemStack itemstack;

        switch (slot.getType()) {
            case HAND:
                itemstack = (ItemStack) this.handItems.get(slot.getIndex());
                break;
            case HUMANOID_ARMOR:
                itemstack = (ItemStack) this.armorItems.get(slot.getIndex());
                break;
            case ANIMAL_ARMOR:
                itemstack = this.bodyArmorItem;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return itemstack;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // Paper start - Fix silent equipment change
        setItemSlot(slot, stack, false);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack, boolean silent) {
        // Paper end - Fix silent equipment change
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND:
                this.onEquipItem(slot, (ItemStack) this.handItems.set(slot.getIndex(), stack), stack, silent); // Paper - Fix silent equipment change
                break;
            case HUMANOID_ARMOR:
                this.onEquipItem(slot, (ItemStack) this.armorItems.set(slot.getIndex(), stack), stack, silent); // Paper - Fix silent equipment change
                break;
            case ANIMAL_ARMOR:
                ItemStack itemstack1 = this.bodyArmorItem;

                this.bodyArmorItem = stack;
                this.onEquipItem(slot, itemstack1, stack, silent); // Paper - Fix silent equipment change
        }

    }

    // Paper start
    protected boolean shouldSkipLoot(EquipmentSlot slot) { // method to avoid to fallback into the global mob loot logic (i.e fox)
        return false;
    }
    // Paper end

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(world, source, causedByPlayer);
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];
            if (this.shouldSkipLoot(enumitemslot)) continue; // Paper
            ItemStack itemstack = this.getItemBySlot(enumitemslot);
            float f = this.getEquipmentDropChance(enumitemslot);

            if (f != 0.0F) {
                boolean flag1 = f > 1.0F;
                Entity entity = source.getEntity();

                if (entity instanceof LivingEntity) {
                    LivingEntity entityliving = (LivingEntity) entity;
                    Level world1 = this.level();

                    if (world1 instanceof ServerLevel) {
                        ServerLevel worldserver1 = (ServerLevel) world1;

                        f = EnchantmentHelper.processEquipmentDropChance(worldserver1, entityliving, source, f);
                    }
                }

                if (!itemstack.isEmpty() && !EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) && (causedByPlayer || flag1) && this.random.nextFloat() < f) {
                    if (!flag1 && itemstack.isDamageableItem()) {
                        itemstack.setDamageValue(itemstack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemstack.getMaxDamage() - 3, 1))));
                    }

                    this.spawnAtLocation(itemstack);
                    if (this.clearEquipmentSlots) { // Paper
                    this.setItemSlot(enumitemslot, ItemStack.EMPTY);
                    // Paper start
                    } else {
                        this.clearedEquipmentSlots.add(enumitemslot);
                    }
                    // Paper end
                }
            }
        }

    }

    public float getEquipmentDropChance(EquipmentSlot slot) {
        float f;

        switch (slot.getType()) {
            case HAND:
                f = this.handDropChances[slot.getIndex()];
                break;
            case HUMANOID_ARMOR:
                f = this.armorDropChances[slot.getIndex()];
                break;
            case ANIMAL_ARMOR:
                f = this.bodyArmorDropChance;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return f;
    }

    public void dropPreservedEquipment() {
        this.dropPreservedEquipment((itemstack) -> {
            return true;
        });
    }

    public Set<EquipmentSlot> dropPreservedEquipment(Predicate<ItemStack> dropPredicate) {
        Set<EquipmentSlot> set = new HashSet();
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];
            ItemStack itemstack = this.getItemBySlot(enumitemslot);

            if (!itemstack.isEmpty()) {
                if (!dropPredicate.test(itemstack)) {
                    set.add(enumitemslot);
                } else {
                    double d0 = (double) this.getEquipmentDropChance(enumitemslot);

                    if (d0 > 1.0D) {
                        this.setItemSlot(enumitemslot, ItemStack.EMPTY);
                        this.spawnAtLocation(itemstack);
                    }
                }
            }
        }

        return set;
    }

    private LootParams createEquipmentParams(ServerLevel world) {
        return (new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.THIS_ENTITY, this).create(LootContextParamSets.EQUIPMENT);
    }

    public void equip(EquipmentTable equipmentTable) {
        this.equip(equipmentTable.lootTable(), equipmentTable.slotDropChances());
    }

    public void equip(ResourceKey<LootTable> lootTable, Map<EquipmentSlot, Float> slotDropChances) {
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            this.equip(lootTable, this.createEquipmentParams(worldserver), slotDropChances);
        }

    }

    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance localDifficulty) {
        if (random.nextFloat() < 0.15F * localDifficulty.getSpecialMultiplier()) {
            int i = random.nextInt(2);
            float f = this.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;

            if (random.nextFloat() < 0.095F) {
                ++i;
            }

            if (random.nextFloat() < 0.095F) {
                ++i;
            }

            if (random.nextFloat() < 0.095F) {
                ++i;
            }

            boolean flag = true;
            EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
            int j = aenumitemslot.length;

            for (int k = 0; k < j; ++k) {
                EquipmentSlot enumitemslot = aenumitemslot[k];

                if (enumitemslot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    ItemStack itemstack = this.getItemBySlot(enumitemslot);

                    if (!flag && random.nextFloat() < f) {
                        break;
                    }

                    flag = false;
                    if (itemstack.isEmpty()) {
                        Item item = Mob.getEquipmentForSlot(enumitemslot, i);

                        if (item != null) {
                            this.setItemSlot(enumitemslot, new ItemStack(item));
                        }
                    }
                }
            }
        }

    }

    @Nullable
    public static Item getEquipmentForSlot(EquipmentSlot equipmentSlot, int equipmentLevel) {
        switch (equipmentSlot) {
            case HEAD:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_HELMET;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_HELMET;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_HELMET;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_HELMET;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_CHESTPLATE;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_LEGGINGS;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_BOOTS;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_BOOTS;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor world, RandomSource random, DifficultyInstance localDifficulty) {
        this.enchantSpawnedWeapon(world, random, localDifficulty);
        EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
        int i = aenumitemslot.length;

        for (int j = 0; j < i; ++j) {
            EquipmentSlot enumitemslot = aenumitemslot[j];

            if (enumitemslot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                this.enchantSpawnedArmor(world, random, enumitemslot, localDifficulty);
            }
        }

    }

    protected void enchantSpawnedWeapon(ServerLevelAccessor world, RandomSource random, DifficultyInstance localDifficulty) {
        this.enchantSpawnedEquipment(world, EquipmentSlot.MAINHAND, random, 0.25F, localDifficulty);
    }

    protected void enchantSpawnedArmor(ServerLevelAccessor world, RandomSource random, EquipmentSlot slot, DifficultyInstance localDifficulty) {
        this.enchantSpawnedEquipment(world, slot, random, 0.5F, localDifficulty);
    }

    private void enchantSpawnedEquipment(ServerLevelAccessor world, EquipmentSlot slot, RandomSource random, float power, DifficultyInstance localDifficulty) {
        ItemStack itemstack = this.getItemBySlot(slot);

        if (!itemstack.isEmpty() && random.nextFloat() < power * localDifficulty.getSpecialMultiplier()) {
            EnchantmentHelper.enchantItemFromProvider(itemstack, world.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, localDifficulty, random);
            this.setItemSlot(slot, itemstack);
        }

    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        RandomSource randomsource = world.getRandom();
        AttributeInstance attributemodifiable = (AttributeInstance) Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE));

        if (!attributemodifiable.hasModifier(Mob.RANDOM_SPAWN_BONUS_ID)) {
            attributemodifiable.addPermanentModifier(new AttributeModifier(Mob.RANDOM_SPAWN_BONUS_ID, randomsource.triangle(0.0D, 0.11485000000000001D), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        this.setLeftHanded(randomsource.nextFloat() < 0.05F);
        return entityData;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float dropChance) {
        switch (slot.getType()) {
            case HAND:
                this.handDropChances[slot.getIndex()] = dropChance;
                break;
            case HUMANOID_ARMOR:
                this.armorDropChances[slot.getIndex()] = dropChance;
                break;
            case ANIMAL_ARMOR:
                this.bodyArmorDropChance = dropChance;
        }

    }

    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean canPickUpLoot) {
        this.canPickUpLoot = canPickUpLoot;
    }

    @Override
    public boolean canTakeItem(ItemStack stack) {
        EquipmentSlot enumitemslot = this.getEquipmentSlotForItem(stack);

        return this.getItemBySlot(enumitemslot).isEmpty() && this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public final InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isAlive()) {
            return InteractionResult.PASS;
        } else {
            InteractionResult enuminteractionresult = this.checkAndHandleImportantInteractions(player, hand);

            if (enuminteractionresult.consumesAction()) {
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                return enuminteractionresult;
            } else {
                InteractionResult enuminteractionresult1 = super.interact(player, hand);

                if (enuminteractionresult1 != InteractionResult.PASS) {
                    return enuminteractionresult1;
                } else {
                    enuminteractionresult = this.mobInteract(player, hand);
                    if (enuminteractionresult.consumesAction()) {
                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        return enuminteractionresult;
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    private InteractionResult checkAndHandleImportantInteractions(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.NAME_TAG)) {
            InteractionResult enuminteractionresult = itemstack.interactLivingEntity(player, this, hand);

            if (enuminteractionresult.consumesAction()) {
                return enuminteractionresult;
            }
        }

        if (itemstack.getItem() instanceof SpawnEggItem) {
            if (this.level() instanceof ServerLevel) {
                SpawnEggItem itemmonsteregg = (SpawnEggItem) itemstack.getItem();
                Optional<Mob> optional = itemmonsteregg.spawnOffspringFromSpawnEgg(player, this, (EntityType<? extends Mob>) this.getType(), (ServerLevel) this.level(), this.position(), itemstack); // CraftBukkit - decompile error

                optional.ifPresent((entityinsentient) -> {
                    this.onOffspringSpawnedFromEgg(player, entityinsentient);
                });
                return optional.isPresent() ? InteractionResult.SUCCESS : InteractionResult.PASS;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {}

    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean isWithinRestriction() {
        return this.isWithinRestriction(this.blockPosition());
    }

    public boolean isWithinRestriction(BlockPos pos) {
        return this.restrictRadius == -1.0F ? true : this.restrictCenter.distSqr(pos) < (double) (this.restrictRadius * this.restrictRadius);
    }

    public void restrictTo(BlockPos target, int range) {
        this.restrictCenter = target;
        this.restrictRadius = (float) range;
    }

    public BlockPos getRestrictCenter() {
        return this.restrictCenter;
    }

    public float getRestrictRadius() {
        return this.restrictRadius;
    }

    public void clearRestriction() {
        this.restrictRadius = -1.0F;
    }

    public boolean hasRestriction() {
        return this.restrictRadius != -1.0F;
    }

    // CraftBukkit start
    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> entityType, boolean keepEquipment) {
        return this.convertTo(entityType, keepEquipment, EntityTransformEvent.TransformReason.UNKNOWN, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> entitytypes, boolean flag, EntityTransformEvent.TransformReason transformReason, CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        if (this.isRemoved()) {
            return null;
        } else {
            T t0 = entitytypes.create(this.level()); // CraftBukkit - decompile error

            if (t0 == null) {
                return null;
            } else {
                t0.copyPosition(this);
                t0.setBaby(this.isBaby());
                t0.setNoAi(this.isNoAi());
                if (this.hasCustomName()) {
                    t0.setCustomName(this.getCustomName());
                    t0.setCustomNameVisible(this.isCustomNameVisible());
                }

                if (this.isPersistenceRequired()) {
                    t0.setPersistenceRequired();
                }

                t0.setInvulnerable(this.isInvulnerable());
                if (flag) {
                    t0.setCanPickUpLoot(this.canPickUpLoot());
                    EquipmentSlot[] aenumitemslot = EquipmentSlot.values();
                    int i = aenumitemslot.length;

                    for (int j = 0; j < i; ++j) {
                        EquipmentSlot enumitemslot = aenumitemslot[j];
                        ItemStack itemstack = this.getItemBySlot(enumitemslot);

                        if (!itemstack.isEmpty()) {
                            t0.setItemSlot(enumitemslot, itemstack.copyAndClear());
                            t0.setDropChance(enumitemslot, this.getEquipmentDropChance(enumitemslot));
                        }
                    }
                }

                // CraftBukkit start
                if (CraftEventFactory.callEntityTransformEvent(this, t0, transformReason).isCancelled()) {
                    return null;
                }
                this.level().addFreshEntity(t0, spawnReason);
                // CraftBukkit end
                if (this.isPassenger()) {
                    Entity entity = this.getVehicle();

                    this.stopRiding();
                    t0.startRiding(entity, true);
                }

                this.discard(EntityRemoveEvent.Cause.TRANSFORMATION); // CraftBukkit - add Bukkit remove cause
                return t0;
            }
        }
    }

    @Nullable
    @Override
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    @Override
    public void dropLeash(boolean sendPacket, boolean dropItem) {
        Leashable.super.dropLeash(sendPacket, dropItem);
        if (this.getLeashData() == null) {
            this.clearRestriction();
        }

    }

    @Override
    public void leashTooFarBehaviour() {
        Leashable.super.leashTooFarBehaviour();
        this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    @Override
    public boolean canBeLeashed() {
        return !(this instanceof Enemy);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        boolean flag1 = super.startRiding(entity, force);

        if (flag1 && this.isLeashed()) {
            // Paper start - Expand EntityUnleashEvent
            EntityUnleashEvent event = new EntityUnleashEvent(this.getBukkitEntity(), EntityUnleashEvent.UnleashReason.UNKNOWN, true);
            if (!event.callEvent()) { return flag1; }
            this.dropLeash(true, event.isDropLeash());
            // Paper end - Expand EntityUnleashEvent
        }

        return flag1;
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    public void setNoAi(boolean aiDisabled) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, aiDisabled ? (byte) (b0 | 1) : (byte) (b0 & -2));
    }

    public void setLeftHanded(boolean leftHanded) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, leftHanded ? (byte) (b0 | 2) : (byte) (b0 & -3));
    }

    public void setAggressive(boolean attacking) {
        byte b0 = (Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID);

        this.entityData.set(Mob.DATA_MOB_FLAGS_ID, attacking ? (byte) (b0 | 4) : (byte) (b0 & -5));
    }

    public boolean isNoAi() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return ((Byte) this.entityData.get(Mob.DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    public void setBaby(boolean baby) {}

    @Override
    public HumanoidArm getMainArm() {
        return this.isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public boolean isWithinMeleeAttackRange(LivingEntity entity) {
        return this.getAttackBoundingBox().intersects(entity.getHitbox());
    }

    protected AABB getAttackBoundingBox() {
        Entity entity = this.getVehicle();
        AABB axisalignedbb;

        if (entity != null) {
            AABB axisalignedbb1 = entity.getBoundingBox();
            AABB axisalignedbb2 = this.getBoundingBox();

            axisalignedbb = new AABB(Math.min(axisalignedbb2.minX, axisalignedbb1.minX), axisalignedbb2.minY, Math.min(axisalignedbb2.minZ, axisalignedbb1.minZ), Math.max(axisalignedbb2.maxX, axisalignedbb1.maxX), axisalignedbb2.maxY, Math.max(axisalignedbb2.maxZ, axisalignedbb1.maxZ));
        } else {
            axisalignedbb = this.getBoundingBox();
        }

        return axisalignedbb.inflate(Mob.DEFAULT_ATTACK_REACH, 0.0D, Mob.DEFAULT_ATTACK_REACH);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float f = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource damagesource = this.damageSources().mobAttack(this);
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            f = EnchantmentHelper.modifyDamage(worldserver, this.getWeaponItem(), target, damagesource, f);
        }

        boolean flag = target.hurt(damagesource, f);

        if (flag) {
            float f1 = this.getKnockback(target, damagesource);

            if (f1 > 0.0F && target instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) target;

                entityliving.knockback((double) (f1 * 0.5F), (double) Mth.sin(this.getYRot() * 0.017453292F), (double) (-Mth.cos(this.getYRot() * 0.017453292F)), this, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK); // CraftBukkit // Paper - knockback events
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }

            Level world1 = this.level();

            if (world1 instanceof ServerLevel) {
                ServerLevel worldserver1 = (ServerLevel) world1;

                EnchantmentHelper.doPostAttackEffects(worldserver1, target, damagesource);
            }

            this.setLastHurtMob(target);
            this.playAttackSound();
        }

        return flag;
    }

    protected void playAttackSound() {}

    public boolean isSunBurnTick() {
        if (this.level().isDay() && !this.level().isClientSide) {
            float f = this.getLightLevelDependentMagicValue();
            BlockPos blockposition = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            boolean flag = this.isInWaterRainOrBubble() || this.isInPowderSnow || this.wasInPowderSnow;

            if (f > 0.5F && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && !flag && this.level().canSeeSky(blockposition)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluid) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(fluid);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.3D, 0.0D));
        }

    }

    @VisibleForTesting
    public void removeFreeWill() {
        this.removeAllGoals((pathfindergoal) -> {
            return true;
        });
        this.getBrain().removeAllBehaviors();
    }

    public void removeAllGoals(Predicate<Goal> predicate) {
        this.goalSelector.removeAllGoals(predicate);
    }

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();
        this.getAllSlots().forEach((itemstack) -> {
            if (!itemstack.isEmpty()) {
                itemstack.setCount(0);
            }

        });
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        SpawnEggItem itemmonsteregg = SpawnEggItem.byId(this.getType());

        return itemmonsteregg == null ? null : new ItemStack(itemmonsteregg);
    }
}
