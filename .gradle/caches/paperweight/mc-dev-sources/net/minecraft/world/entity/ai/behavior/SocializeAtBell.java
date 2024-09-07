package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SocializeAtBell {
    private static final float SPEED_MODIFIER = 0.3F;

    public static OneShot<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.present(MemoryModuleType.MEETING_POINT),
                        context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES),
                        context.absent(MemoryModuleType.INTERACTION_TARGET)
                    )
                    .apply(
                        context,
                        (walkTarget, lookTarget, meetingPoint, visibleMobs, interactionTarget) -> (world, entity, time) -> {
                                GlobalPos globalPos = context.get(meetingPoint);
                                NearestVisibleLivingEntities nearestVisibleLivingEntities = context.get(visibleMobs);
                                if (world.getRandom().nextInt(100) == 0
                                    && world.dimension() == globalPos.dimension()
                                    && globalPos.pos().closerToCenterThan(entity.position(), 4.0)
                                    && nearestVisibleLivingEntities.contains(target -> EntityType.VILLAGER.equals(target.getType()))) {
                                    nearestVisibleLivingEntities.findClosest(
                                            target -> EntityType.VILLAGER.equals(target.getType()) && target.distanceToSqr(entity) <= 32.0
                                        )
                                        .ifPresent(target -> {
                                            interactionTarget.set(target);
                                            lookTarget.set(new EntityTracker(target, true));
                                            walkTarget.set(new WalkTarget(new EntityTracker(target, false), 0.3F, 1));
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
