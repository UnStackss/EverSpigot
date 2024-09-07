package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface ProjectileDeflection {
    ProjectileDeflection NONE = (projectile, hitEntity, random) -> {
    };
    ProjectileDeflection REVERSE = (projectile, hitEntity, random) -> {
        float f = 170.0F + random.nextFloat() * 20.0F;
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.5));
        projectile.setYRot(projectile.getYRot() + f);
        projectile.yRotO += f;
        projectile.hasImpulse = true;
    };
    ProjectileDeflection AIM_DEFLECT = (projectile, hitEntity, random) -> {
        if (hitEntity != null) {
            Vec3 vec3 = hitEntity.getLookAngle().normalize();
            projectile.setDeltaMovement(vec3);
            projectile.hasImpulse = true;
        }
    };
    ProjectileDeflection MOMENTUM_DEFLECT = (projectile, hitEntity, random) -> {
        if (hitEntity != null) {
            Vec3 vec3 = hitEntity.getDeltaMovement().normalize();
            projectile.setDeltaMovement(vec3);
            projectile.hasImpulse = true;
        }
    };

    void deflect(Projectile projectile, @Nullable Entity hitEntity, RandomSource random);
}
