package net.minecraft.world.effect;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

class HealOrHarmMobEffect extends InstantenousMobEffect {

    private final boolean isHarm;

    public HealOrHarmMobEffect(MobEffectCategory category, int color, boolean damage) {
        super(category, color);
        this.isHarm = damage;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (this.isHarm == entity.isInvertedHealAndHarm()) {
            entity.heal((float) Math.max(4 << amplifier, 0), org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.MAGIC); // CraftBukkit
        } else {
            entity.hurt(entity.damageSources().magic(), (float) (6 << amplifier));
        }

        return true;
    }

    @Override
    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
        int j;

        if (this.isHarm == target.isInvertedHealAndHarm()) {
            j = (int) (proximity * (double) (4 << amplifier) + 0.5D);
            target.heal((float) j, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.MAGIC); // CraftBukkit
        } else {
            j = (int) (proximity * (double) (6 << amplifier) + 0.5D);
            if (source == null) {
                target.hurt(target.damageSources().magic(), (float) j);
            } else {
                target.hurt(target.damageSources().indirectMagic(source, attacker), (float) j);
            }
        }

    }
}
