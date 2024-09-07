package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.GameRules;

public class StartCelebratingIfTargetDead {
    public static BehaviorControl<LivingEntity> create(int celebrationDuration, BiPredicate<LivingEntity, LivingEntity> predicate) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(MemoryModuleType.ATTACK_TARGET),
                        context.registered(MemoryModuleType.ANGRY_AT),
                        context.absent(MemoryModuleType.CELEBRATE_LOCATION),
                        context.registered(MemoryModuleType.DANCING)
                    )
                    .apply(context, (attackTarget, angryAt, celebrateLocation, dancing) -> (world, entity, time) -> {
                            LivingEntity livingEntity = context.get(attackTarget);
                            if (!livingEntity.isDeadOrDying()) {
                                return false;
                            } else {
                                if (predicate.test(entity, livingEntity)) {
                                    dancing.setWithExpiry(true, (long)celebrationDuration);
                                }

                                celebrateLocation.setWithExpiry(livingEntity.blockPosition(), (long)celebrationDuration);
                                if (livingEntity.getType() != EntityType.PLAYER || world.getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
                                    attackTarget.erase();
                                    angryAt.erase();
                                }

                                return true;
                            }
                        })
        );
    }
}
