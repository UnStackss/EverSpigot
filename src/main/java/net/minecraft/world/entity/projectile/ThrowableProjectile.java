package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class ThrowableProjectile extends Projectile {

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> type, Level world) {
        super(type, world);
    }

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> type, double x, double y, double z, Level world) {
        this(type, world);
        this.setPos(x, y, z);
    }

    protected ThrowableProjectile(EntityType<? extends ThrowableProjectile> type, LivingEntity owner, Level world) {
        this(type, owner.getX(), owner.getEyeY() - 0.10000000149011612D, owner.getZ(), world);
        this.setOwner(owner);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1)) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    @Override
    public boolean canUsePortal(boolean allowVehicles) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        HitResult movingobjectposition = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        if (movingobjectposition.getType() != HitResult.Type.MISS) {
            this.preHitTargetOrDeflectSelf(movingobjectposition); // CraftBukkit - projectile hit event
        }

        this.checkInsideBlocks();
        Vec3 vec3d = this.getDeltaMovement();
        double d0 = this.getX() + vec3d.x;
        double d1 = this.getY() + vec3d.y;
        double d2 = this.getZ() + vec3d.z;

        this.updateRotation();
        float f;

        if (this.isInWater()) {
            for (int i = 0; i < 4; ++i) {
                float f1 = 0.25F;

                this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3d.x * 0.25D, d1 - vec3d.y * 0.25D, d2 - vec3d.z * 0.25D, vec3d.x, vec3d.y, vec3d.z);
            }

            f = 0.8F;
        } else {
            f = 0.99F;
        }

        this.setDeltaMovement(vec3d.scale((double) f));
        this.applyGravity();
        this.setPos(d0, d1, d2);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03D;
    }
}
