package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class SetWalkTargetAwayFrom {
    public static BehaviorControl<PathfinderMob> pos(MemoryModuleType<BlockPos> posModule, float speed, int range, boolean requiresWalkTarget) {
        return create(posModule, speed, range, requiresWalkTarget, Vec3::atBottomCenterOf);
    }

    public static OneShot<PathfinderMob> entity(MemoryModuleType<? extends Entity> entityModule, float speed, int range, boolean requiresWalkTarget) {
        return create(entityModule, speed, range, requiresWalkTarget, Entity::position);
    }

    private static <T> OneShot<PathfinderMob> create(
        MemoryModuleType<T> posSource, float speed, int range, boolean requiresWalkTarget, Function<T, Vec3> posGetter
    ) {
        return BehaviorBuilder.create(
            context -> context.group(context.registered(MemoryModuleType.WALK_TARGET), context.present(posSource))
                    .apply(context, (walkTarget, posSourcex) -> (world, entity, time) -> {
                            Optional<WalkTarget> optional = context.tryGet(walkTarget);
                            if (optional.isPresent() && !requiresWalkTarget) {
                                return false;
                            } else {
                                Vec3 vec3 = entity.position();
                                Vec3 vec32 = posGetter.apply(context.get(posSourcex));
                                if (!vec3.closerThan(vec32, (double)range)) {
                                    return false;
                                } else {
                                    if (optional.isPresent() && optional.get().getSpeedModifier() == speed) {
                                        Vec3 vec33 = optional.get().getTarget().currentPosition().subtract(vec3);
                                        Vec3 vec34 = vec32.subtract(vec3);
                                        if (vec33.dot(vec34) < 0.0) {
                                            return false;
                                        }
                                    }

                                    for (int j = 0; j < 10; j++) {
                                        Vec3 vec35 = LandRandomPos.getPosAway(entity, 16, 7, vec32);
                                        if (vec35 != null) {
                                            walkTarget.set(new WalkTarget(vec35, speed, 0));
                                            break;
                                        }
                                    }

                                    return true;
                                }
                            }
                        })
        );
    }
}
