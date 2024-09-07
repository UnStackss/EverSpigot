package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetEntityLookTarget {
    public static BehaviorControl<LivingEntity> create(MobCategory spawnGroup, float maxDistance) {
        return create(entity -> spawnGroup.equals(entity.getType().getCategory()), maxDistance);
    }

    public static OneShot<LivingEntity> create(EntityType<?> type, float maxDistance) {
        return create(entity -> type.equals(entity.getType()), maxDistance);
    }

    public static OneShot<LivingEntity> create(float maxDistance) {
        return create(entity -> true, maxDistance);
    }

    public static OneShot<LivingEntity> create(Predicate<LivingEntity> predicate, float maxDistance) {
        float f = maxDistance * maxDistance;
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.LOOK_TARGET), context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES))
                    .apply(
                        context,
                        (lookTarget, visibleMobs) -> (world, entity, time) -> {
                                Optional<LivingEntity> optional = context.<NearestVisibleLivingEntities>get(visibleMobs)
                                    .findClosest(predicate.and(target -> target.distanceToSqr(entity) <= (double)f && !entity.hasPassenger(target)));
                                if (optional.isEmpty()) {
                                    return false;
                                } else {
                                    lookTarget.set(new EntityTracker(optional.get(), true));
                                    return true;
                                }
                            }
                    )
        );
    }
}
