package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class AbsorptionMobEffect extends MobEffect {
    protected AbsorptionMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return entity.getAbsorptionAmount() > 0.0F || entity.level().isClientSide;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), (float)(4 * (1 + amplifier))));
    }
}
