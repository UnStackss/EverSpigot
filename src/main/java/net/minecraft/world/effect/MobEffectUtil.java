package net.minecraft.world.effect;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class MobEffectUtil {

    public MobEffectUtil() {}

    public static Component formatDuration(MobEffectInstance effect, float multiplier, float tickRate) {
        if (effect.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        } else {
            int i = Mth.floor((float) effect.getDuration() * multiplier);

            return Component.literal(StringUtil.formatTickDuration(i, tickRate));
        }
    }

    public static boolean hasDigSpeed(LivingEntity entity) {
        return entity.hasEffect(MobEffects.DIG_SPEED) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static int getDigSpeedAmplification(LivingEntity entity) {
        int i = 0;
        int j = 0;

        if (entity.hasEffect(MobEffects.DIG_SPEED)) {
            i = entity.getEffect(MobEffects.DIG_SPEED).getAmplifier();
        }

        if (entity.hasEffect(MobEffects.CONDUIT_POWER)) {
            j = entity.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
        }

        return Math.max(i, j);
    }

    public static boolean hasWaterBreathing(LivingEntity entity) {
        return entity.hasEffect(MobEffects.WATER_BREATHING) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(ServerLevel world, @Nullable Entity entity, Vec3 origin, double range, MobEffectInstance statusEffectInstance, int duration) {
        // CraftBukkit start
        return MobEffectUtil.addEffectToPlayersAround(world, entity, origin, range, statusEffectInstance, duration, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(ServerLevel worldserver, @Nullable Entity entity, Vec3 vec3d, double d0, MobEffectInstance mobeffect, int i, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        // Paper start - Add ElderGuardianAppearanceEvent
        return addEffectToPlayersAround(worldserver, entity, vec3d, d0, mobeffect, i, cause, null);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(ServerLevel worldserver, @Nullable Entity entity, Vec3 vec3d, double d0, MobEffectInstance mobeffect, int i, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause, @Nullable java.util.function.Predicate<ServerPlayer> playerPredicate) {
        // Paper end - Add ElderGuardianAppearanceEvent
        // CraftBukkit end
        Holder<MobEffect> holder = mobeffect.getEffect();
        List<ServerPlayer> list = worldserver.getPlayers((entityplayer) -> {
            // Paper start - Add ElderGuardianAppearanceEvent
            boolean condition = entityplayer.gameMode.isSurvival() && (entity == null || !entity.isAlliedTo((Entity) entityplayer)) && vec3d.closerThan(entityplayer.position(), d0) && (!entityplayer.hasEffect(holder) || entityplayer.getEffect(holder).getAmplifier() < mobeffect.getAmplifier() || entityplayer.getEffect(holder).endsWithin(i - 1));
            if (condition) {
                return playerPredicate == null || playerPredicate.test(entityplayer); // Only test the player AFTER it is true
            } else {
                return false;
            }
            // Paper ned - Add ElderGuardianAppearanceEvent
        });

        list.forEach((entityplayer) -> {
            entityplayer.addEffect(new MobEffectInstance(mobeffect), entity, cause); // CraftBukkit
        });
        return list;
    }
}
