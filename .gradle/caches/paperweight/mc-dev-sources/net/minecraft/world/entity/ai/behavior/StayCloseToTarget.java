package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class StayCloseToTarget {
    public static BehaviorControl<LivingEntity> create(
        Function<LivingEntity, Optional<PositionTracker>> lookTargetFunction,
        Predicate<LivingEntity> predicate,
        int completionRange,
        int searchRange,
        float speed
    ) {
        return BehaviorBuilder.create(
            context -> context.group(context.registered(MemoryModuleType.LOOK_TARGET), context.registered(MemoryModuleType.WALK_TARGET))
                    .apply(context, (lookTarget, walkTarget) -> (world, entity, time) -> {
                            Optional<PositionTracker> optional = lookTargetFunction.apply(entity);
                            if (!optional.isEmpty() && predicate.test(entity)) {
                                PositionTracker positionTracker = optional.get();
                                if (entity.position().closerThan(positionTracker.currentPosition(), (double)searchRange)) {
                                    return false;
                                } else {
                                    PositionTracker positionTracker2 = optional.get();
                                    lookTarget.set(positionTracker2);
                                    walkTarget.set(new WalkTarget(positionTracker2, speed, completionRange));
                                    return true;
                                }
                            } else {
                                return false;
                            }
                        })
        );
    }
}
