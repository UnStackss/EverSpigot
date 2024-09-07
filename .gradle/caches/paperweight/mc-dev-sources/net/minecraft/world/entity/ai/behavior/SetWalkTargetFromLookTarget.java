package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SetWalkTargetFromLookTarget {
    public static OneShot<LivingEntity> create(float speed, int completionRange) {
        return create(entity -> true, entity -> speed, completionRange);
    }

    public static OneShot<LivingEntity> create(Predicate<LivingEntity> predicate, Function<LivingEntity, Float> speed, int completionRange) {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.WALK_TARGET), context.present(MemoryModuleType.LOOK_TARGET))
                    .apply(context, (walkTarget, lookTarget) -> (world, entity, time) -> {
                            if (!predicate.test(entity)) {
                                return false;
                            } else {
                                walkTarget.set(new WalkTarget(context.get(lookTarget), speed.apply(entity), completionRange));
                                return true;
                            }
                        })
        );
    }
}
