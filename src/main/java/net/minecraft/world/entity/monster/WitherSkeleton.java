package net.minecraft.world.entity.monster;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;

public class WitherSkeleton extends AbstractSkeleton {

    public WitherSkeleton(EntityType<? extends WitherSkeleton> type, Level world) {
        super(type, world);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractPiglin.class, true));
        super.registerGoals();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.WITHER_SKELETON_STEP;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(world, source, causedByPlayer);
        Entity entity = source.getEntity();

        if (entity instanceof Creeper entitycreeper) {
            if (entitycreeper.canDropMobsSkull()) {
                entitycreeper.increaseDroppedSkulls();
                this.spawnAtLocation((ItemLike) Items.WITHER_SKELETON_SKULL);
            }
        }

    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance localDifficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
    }

    @Override
    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor world, RandomSource random, DifficultyInstance localDifficulty) {}

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        SpawnGroupData groupdataentity1 = super.finalizeSpawn(world, difficulty, spawnReason, entityData);

        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0D);
        this.reassessWeaponGoal();
        return groupdataentity1;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!super.doHurtTarget(target)) {
            return false;
        } else {
            if (target instanceof LivingEntity) {
                ((LivingEntity) target).addEffect(new MobEffectInstance(MobEffects.WITHER, 200), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
            }

            return true;
        }
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float damageModifier, @Nullable ItemStack shotFrom) {
        AbstractArrow entityarrow = super.getArrow(arrow, damageModifier, shotFrom);

        entityarrow.igniteForSeconds(100.0F);
        return entityarrow;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.is(MobEffects.WITHER) && this.level().paperConfig().entities.mobEffects.immuneToWitherEffect.witherSkeleton ? false : super.canBeAffected(effect); // Paper - Add config for mobs immune to default effects
    }
}
