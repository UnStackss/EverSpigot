package net.minecraft.world.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;

class WindChargedMobEffect extends MobEffect {
    protected WindChargedMobEffect(MobEffectCategory category, int color) {
        super(category, color, ParticleTypes.SMALL_GUST);
    }

    @Override
    public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED && entity.level() instanceof ServerLevel serverLevel) {
            double d = entity.getX();
            double e = entity.getY() + (double)(entity.getBbHeight() / 2.0F);
            double f = entity.getZ();
            float g = 3.0F + entity.getRandom().nextFloat() * 2.0F;
            serverLevel.explode(
                entity,
                null,
                AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR,
                d,
                e,
                f,
                g,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.BREEZE_WIND_CHARGE_BURST
            );
        }
    }
}
