package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.GameRules;

public class StopBeingAngryIfTargetDead {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.ANGRY_AT))
                    .apply(
                        context,
                        angryAt -> (world, entity, time) -> {
                                Optional.ofNullable(world.getEntity(context.get(angryAt)))
                                    .map(target -> target instanceof LivingEntity livingEntity ? livingEntity : null)
                                    .filter(LivingEntity::isDeadOrDying)
                                    .filter(
                                        target -> target.getType() != EntityType.PLAYER || world.getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)
                                    )
                                    .ifPresent(target -> angryAt.erase());
                                return true;
                            }
                    )
        );
    }
}
