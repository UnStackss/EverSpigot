package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class Mount {
    private static final int CLOSE_ENOUGH_TO_START_RIDING_DIST = 1;

    public static BehaviorControl<LivingEntity> create(float speed) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.present(MemoryModuleType.RIDE_TARGET)
                    )
                    .apply(context, (lookTarget, walkTarget, rideTarget) -> (world, entity, time) -> {
                            if (entity.isPassenger()) {
                                return false;
                            } else {
                                Entity entity2 = context.get(rideTarget);
                                if (entity2.closerThan(entity, 1.0)) {
                                    entity.startRiding(entity2);
                                } else {
                                    lookTarget.set(new EntityTracker(entity2, true));
                                    walkTarget.set(new WalkTarget(new EntityTracker(entity2, false), speed, 1));
                                }

                                return true;
                            }
                        })
        );
    }
}
