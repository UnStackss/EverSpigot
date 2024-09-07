package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractWith {
    public static <T extends LivingEntity> BehaviorControl<LivingEntity> of(
        EntityType<? extends T> type, int maxDistance, MemoryModuleType<T> targetModule, float speed, int completionRange
    ) {
        return of(type, maxDistance, entity -> true, entity -> true, targetModule, speed, completionRange);
    }

    public static <E extends LivingEntity, T extends LivingEntity> BehaviorControl<E> of(
        EntityType<? extends T> type,
        int maxDistance,
        Predicate<E> entityPredicate,
        Predicate<T> targetPredicate,
        MemoryModuleType<T> targetModule,
        float speed,
        int completionRange
    ) {
        int i = maxDistance * maxDistance;
        Predicate<LivingEntity> predicate = entity -> type.equals(entity.getType()) && targetPredicate.test((T)entity);
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(targetModule),
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(
                        context,
                        (targetValue, lookTarget, walkTarget, visibleMobs) -> (world, entity, time) -> {
                                NearestVisibleLivingEntities nearestVisibleLivingEntities = context.get(visibleMobs);
                                if (entityPredicate.test(entity) && nearestVisibleLivingEntities.contains(predicate)) {
                                    Optional<LivingEntity> optional = nearestVisibleLivingEntities.findClosest(
                                        target -> target.distanceToSqr(entity) <= (double)i && predicate.test(target)
                                    );
                                    optional.ifPresent(target -> {
                                        targetValue.set(target);
                                        lookTarget.set(new EntityTracker(target, true));
                                        walkTarget.set(new WalkTarget(new EntityTracker(target, false), speed, completionRange));
                                    });
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                    )
        );
    }
}
