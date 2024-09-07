package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class EraseMemoryIf {
    public static <E extends LivingEntity> BehaviorControl<E> create(Predicate<E> condition, MemoryModuleType<?> memory) {
        return BehaviorBuilder.create(context -> context.group(context.present(memory)).apply(context, queryResult -> (world, entity, time) -> {
                    if (condition.test(entity)) {
                        queryResult.erase();
                        return true;
                    } else {
                        return false;
                    }
                }));
    }
}
