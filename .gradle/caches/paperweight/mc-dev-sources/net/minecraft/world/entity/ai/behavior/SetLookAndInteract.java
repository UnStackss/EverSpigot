package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class SetLookAndInteract {
    public static BehaviorControl<LivingEntity> create(EntityType<?> type, int maxDistance) {
        int i = maxDistance * maxDistance;
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.absent(MemoryModuleType.INTERACTION_TARGET),
                        context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(
                        context,
                        (lookTarget, interactionTarget, visibleMobs) -> (world, entity, time) -> {
                                Optional<LivingEntity> optional = context.<NearestVisibleLivingEntities>get(visibleMobs)
                                    .findClosest(target -> target.distanceToSqr(entity) <= (double)i && type.equals(target.getType()));
                                if (optional.isEmpty()) {
                                    return false;
                                } else {
                                    LivingEntity livingEntity = optional.get();
                                    interactionTarget.set(livingEntity);
                                    lookTarget.set(new EntityTracker(livingEntity, true));
                                    return true;
                                }
                            }
                    )
        );
    }
}
