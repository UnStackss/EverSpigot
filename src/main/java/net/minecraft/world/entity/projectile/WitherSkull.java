package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class WitherSkull extends AbstractHurtingProjectile {

    private static final EntityDataAccessor<Boolean> DATA_DANGEROUS = SynchedEntityData.defineId(WitherSkull.class, EntityDataSerializers.BOOLEAN);

    public WitherSkull(EntityType<? extends WitherSkull> type, Level world) {
        super(type, world);
    }

    public WitherSkull(Level world, LivingEntity owner, Vec3 velocity) {
        super(EntityType.WITHER_SKULL, owner, velocity, world);
    }

    @Override
    protected float getInertia() {
        return this.isDangerous() ? 0.73F : super.getInertia();
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public float getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState, float max) {
        return this.isDangerous() && WitherBoss.canDestroy(blockState) ? Math.min(0.8F, max) : max;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            Entity entity = entityHitResult.getEntity();
            Entity entity1 = this.getOwner();
            boolean flag;

            if (entity1 instanceof LivingEntity entityliving) {
                DamageSource damagesource = this.damageSources().witherSkull(this, entityliving);

                flag = entity.hurt(damagesource, 8.0F);
                if (flag) {
                    if (entity.isAlive()) {
                        EnchantmentHelper.doPostAttackEffects(worldserver, entity, damagesource);
                    } else {
                        entityliving.heal(5.0F, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.WITHER); // CraftBukkit
                    }
                }
            } else {
                flag = entity.hurt(this.damageSources().magic().customEventDamager(this), 5.0F); // Paper - Fire EntityDamageByEntityEvent for unowned wither skulls // Paper - fix DamageSource API
            }

            if (flag && entity instanceof LivingEntity entityliving) {
                byte b0 = 0;

                if (this.level().getDifficulty() == Difficulty.NORMAL) {
                    b0 = 10;
                } else if (this.level().getDifficulty() == Difficulty.HARD) {
                    b0 = 40;
                }

                if (b0 > 0) {
                    entityliving.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * b0, 1), this.getEffectSource(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.ATTACK); // CraftBukkit
                }
            }

        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            // CraftBukkit start
            // this.level().explode(this, this.getX(), this.getY(), this.getZ(), 1.0F, false, World.a.MOB);
            ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), 1.0F, false);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                this.level().explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.MOB);
            }
            // CraftBukkit end
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WitherSkull.DATA_DANGEROUS, false);
    }

    public boolean isDangerous() {
        return (Boolean) this.entityData.get(WitherSkull.DATA_DANGEROUS);
    }

    public void setDangerous(boolean charged) {
        this.entityData.set(WitherSkull.DATA_DANGEROUS, charged);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("dangerous", this.isDangerous());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setDangerous(nbt.getBoolean("dangerous"));
    }
}
