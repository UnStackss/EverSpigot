package net.minecraft.world.entity.monster.piglin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class RememberIfHoglinWasKilled {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.ATTACK_TARGET), context.registered(MemoryModuleType.HUNTED_RECENTLY))
                    .apply(context, (attackTarget, huntedRecently) -> (world, entity, time) -> {
                            LivingEntity livingEntity = context.get(attackTarget);
                            if (livingEntity.getType() == EntityType.HOGLIN && livingEntity.isDeadOrDying()) {
                                huntedRecently.setWithExpiry(true, (long)PiglinAi.TIME_BETWEEN_HUNTS.sample(entity.level().random));
                            }

                            return true;
                        })
        );
    }
}
