package net.minecraft.world.entity.projectile;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class DragonFireball extends AbstractHurtingProjectile {

    public static final float SPLASH_RANGE = 4.0F;

    public DragonFireball(EntityType<? extends DragonFireball> type, Level world) {
        super(type, world);
    }

    public DragonFireball(Level world, LivingEntity owner, Vec3 velocity) {
        super(EntityType.DRAGON_FIREBALL, owner, velocity, world);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (hitResult.getType() != HitResult.Type.ENTITY || !this.ownedBy(((EntityHitResult) hitResult).getEntity())) {
            if (!this.level().isClientSide) {
                List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D));
                AreaEffectCloud entityareaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
                Entity entity = this.getOwner();

                if (entity instanceof LivingEntity) {
                    entityareaeffectcloud.setOwner((LivingEntity) entity);
                }

                entityareaeffectcloud.setParticle(ParticleTypes.DRAGON_BREATH);
                entityareaeffectcloud.setRadius(3.0F);
                entityareaeffectcloud.setDuration(600);
                entityareaeffectcloud.setRadiusPerTick((7.0F - entityareaeffectcloud.getRadius()) / (float) entityareaeffectcloud.getDuration());
                entityareaeffectcloud.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 1));
                if (!list.isEmpty()) {
                    Iterator iterator = list.iterator();

                    while (iterator.hasNext()) {
                        LivingEntity entityliving = (LivingEntity) iterator.next();
                        double d0 = this.distanceToSqr((Entity) entityliving);

                        if (d0 < 16.0D) {
                            entityareaeffectcloud.setPos(entityliving.getX(), entityliving.getY(), entityliving.getZ());
                            break;
                        }
                    }
                }

                if (new com.destroystokyo.paper.event.entity.EnderDragonFireballHitEvent((org.bukkit.entity.DragonFireball) this.getBukkitEntity(), list.stream().map(LivingEntity::getBukkitLivingEntity).collect(java.util.stream.Collectors.toList()), (org.bukkit.entity.AreaEffectCloud) entityareaeffectcloud.getBukkitEntity()).callEvent()) { // Paper - EnderDragon Events
                this.level().levelEvent(2006, this.blockPosition(), this.isSilent() ? -1 : 1);
                this.level().addFreshEntity(entityareaeffectcloud, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EXPLOSION); // Paper - use correct spawn reason
                } else entityareaeffectcloud.discard(null); // Paper - EnderDragon Events
                this.discard(EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            }

        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.DRAGON_BREATH;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }
}
