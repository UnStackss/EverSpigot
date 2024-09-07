package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class CopyMemoryWithExpiry {
    public static <E extends LivingEntity, T> BehaviorControl<E> create(
        Predicate<E> runPredicate, MemoryModuleType<? extends T> sourceType, MemoryModuleType<T> targetType, UniformInt expiry
    ) {
        return BehaviorBuilder.create(
            context -> context.group(context.present(sourceType), context.absent(targetType)).apply(context, (source, target) -> (world, entity, time) -> {
                        if (!runPredicate.test(entity)) {
                            return false;
                        } else {
                            target.setWithExpiry(context.get(source), (long)expiry.sample(world.random));
                            return true;
                        }
                    })
        );
    }
}
