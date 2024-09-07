package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class SetWalkTargetFromAttackTargetIfTargetOutOfReach {
    private static final int PROJECTILE_ATTACK_RANGE_BUFFER = 1;

    public static BehaviorControl<Mob> create(float speed) {
        return create(entity -> speed);
    }

    public static BehaviorControl<Mob> create(Function<LivingEntity, Float> speed) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET),
                        context.present(MemoryModuleType.ATTACK_TARGET),
                        context.registered(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                    )
                    .apply(context, (walkTarget, lookTarget, attackTarget, visibleMobs) -> (world, entity, time) -> {
                            LivingEntity livingEntity = context.get(attackTarget);
                            Optional<NearestVisibleLivingEntities> optional = context.tryGet(visibleMobs);
                            if (optional.isPresent() && optional.get().contains(livingEntity) && BehaviorUtils.isWithinAttackRange(entity, livingEntity, 1)) {
                                walkTarget.erase();
                            } else {
                                lookTarget.set(new EntityTracker(livingEntity, true));
                                walkTarget.set(new WalkTarget(new EntityTracker(livingEntity, false), speed.apply(entity), 0));
                            }

                            return true;
                        })
        );
    }
}
