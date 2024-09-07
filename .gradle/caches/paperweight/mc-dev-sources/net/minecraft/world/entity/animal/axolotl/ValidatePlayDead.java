package net.minecraft.world.entity.animal.axolotl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class ValidatePlayDead {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.PLAY_DEAD_TICKS), context.registered(MemoryModuleType.HURT_BY_ENTITY))
                    .apply(context, (playDeadTicks, hurtByEntity) -> (world, entity, time) -> {
                            int i = context.get(playDeadTicks);
                            if (i <= 0) {
                                playDeadTicks.erase();
                                hurtByEntity.erase();
                                entity.getBrain().useDefaultActivity();
                            } else {
                                playDeadTicks.set(i - 1);
                            }

                            return true;
                        })
        );
    }
}
