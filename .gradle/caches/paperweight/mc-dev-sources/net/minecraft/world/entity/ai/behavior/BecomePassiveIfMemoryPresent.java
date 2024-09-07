package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class BecomePassiveIfMemoryPresent {
    public static BehaviorControl<LivingEntity> create(MemoryModuleType<?> requiredMemory, int duration) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.ATTACK_TARGET), context.absent(MemoryModuleType.PACIFIED), context.present(requiredMemory)
                    )
                    .apply(
                        context,
                        context.point(
                            () -> "[BecomePassive if " + requiredMemory + " present]",
                            (attackTarget, pacified, requiredMemoryResult) -> (world, entity, time) -> {
                                    pacified.setWithExpiry(true, (long)duration);
                                    attackTarget.erase();
                                    return true;
                                }
                        )
                    )
        );
    }
}
