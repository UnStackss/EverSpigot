package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;

class BadOmenMobEffect extends MobEffect {
    protected BadOmenMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
            ServerLevel serverLevel = serverPlayer.serverLevel();
            if (serverLevel.getDifficulty() != Difficulty.PEACEFUL && serverLevel.isVillage(serverPlayer.blockPosition())) {
                Raid raid = serverLevel.getRaidAt(serverPlayer.blockPosition());
                if (raid == null || raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
                    serverPlayer.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, amplifier));
                    serverPlayer.setRaidOmenPosition(serverPlayer.blockPosition());
                    return false;
                }
            }
        }

        return true;
    }
}
