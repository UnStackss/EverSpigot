package net.minecraft.world.entity.projectile.windcharge;

import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public abstract class AbstractWindCharge extends AbstractHurtingProjectile implements ItemSupplier {

    public static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(true, false, Optional.empty(), BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));
    public static final double JUMP_SCALE = 0.25D;

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> type, Level world) {
        super(type, world);
        this.accelerationPower = 0.0D;
    }

    public AbstractWindCharge(EntityType<? extends AbstractWindCharge> type, Level world, Entity owner, double x, double y, double z) {
        super(type, x, y, z, world);
        this.setOwner(owner);
        this.accelerationPower = 0.0D;
    }

    AbstractWindCharge(EntityType<? extends AbstractWindCharge> type, double x, double y, double z, Vec3 velocity, Level world) {
        super(type, x, y, z, velocity, world);
        this.accelerationPower = 0.0D;
    }

    @Override
    protected AABB makeBoundingBox() {
        float f = this.getType().getDimensions().width() / 2.0F;
        float f1 = this.getType().getDimensions().height();
        float f2 = 0.15F;

        return new AABB(this.position().x - (double) f, this.position().y - 0.15000000596046448D, this.position().z - (double) f, this.position().x + (double) f, this.position().y - 0.15000000596046448D + (double) f1, this.position().z + (double) f);
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return other instanceof AbstractWindCharge ? false : super.canCollideWith(other);
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return entity instanceof AbstractWindCharge ? false : (entity.getType() == EntityType.END_CRYSTAL ? false : super.canHitEntity(entity));
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (!this.level().isClientSide) {
            Entity entity = this.getOwner();
            LivingEntity entityliving;

            if (entity instanceof LivingEntity) {
                LivingEntity entityliving1 = (LivingEntity) entity;

                entityliving = entityliving1;
            } else {
                entityliving = null;
            }

            LivingEntity entityliving2 = entityliving;
            Entity entity1 = entityHitResult.getEntity();

            if (entityliving2 != null) {
                entityliving2.setLastHurtMob(entity1);
            }

            DamageSource damagesource = this.damageSources().windCharge(this, entityliving2);

            if (entity1.hurt(damagesource, 1.0F) && entity1 instanceof LivingEntity) {
                LivingEntity entityliving3 = (LivingEntity) entity1;

                EnchantmentHelper.doPostAttackEffects((ServerLevel) this.level(), entityliving3, damagesource);
            }

            this.explode(this.position());
        }
    }

    @Override
    public void push(double deltaX, double deltaY, double deltaZ) {}

    public abstract void explode(Vec3 pos);

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide) {
            Vec3i baseblockposition = blockHitResult.getDirection().getNormal();
            Vec3 vec3d = Vec3.atLowerCornerOf(baseblockposition).multiply(0.25D, 0.25D, 0.25D);
            Vec3 vec3d1 = blockHitResult.getLocation().add(vec3d);

            this.explode(vec3d1);
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide) {
            this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return this.getInertia();
    }

    @Nullable
    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.getBlockY() > this.level().getMaxBuildHeight() + 30) {
            this.explode(this.position());
            this.discard(EntityRemoveEvent.Cause.OUT_OF_WORLD); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
