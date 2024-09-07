package net.minecraft.world.entity.ai.behavior;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class BackUpIfTooClose {
    public static OneShot<Mob> create(int distance, float forwardMovement) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.present(MemoryModuleType.ATTACK_TARGET),
                        context.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(context, (walkTarget, lookTarget, attackTarget, visibleMobs) -> (world, entity, time) -> {
                            LivingEntity livingEntity = context.get(attackTarget);
                            if (livingEntity.closerThan(entity, (double)distance)
                                && context.<NearestVisibleLivingEntities>get(visibleMobs).contains(livingEntity)) {
                                lookTarget.set(new EntityTracker(livingEntity, true));
                                entity.getMoveControl().strafe(-forwardMovement, 0.0F);
                                entity.setYRot(Mth.rotateIfNecessary(entity.getYRot(), entity.yHeadRot, 0.0F));
                                return true;
                            } else {
                                return false;
                            }
                        })
        );
    }
}
