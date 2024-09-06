package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;

class PoisonMobEffect extends MobEffect {

    protected PoisonMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() > 1.0F) {
            entity.hurt(entity.damageSources().poison(), 1.0F);  // CraftBukkit - DamageSource.MAGIC -> CraftEventFactory.POISON
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int k = 25 >> amplifier;

        return k > 0 ? duration % k == 0 : true;
    }
}
