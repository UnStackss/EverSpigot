package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

public class RandomStroll {
    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;
    private static final int[][] SWIM_XY_DISTANCE_TIERS = new int[][]{{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};

    public static OneShot<PathfinderMob> stroll(float speed) {
        return stroll(speed, true);
    }

    public static OneShot<PathfinderMob> stroll(float speed, boolean strollInsideWater) {
        return strollFlyOrSwim(speed, entity -> LandRandomPos.getPos(entity, 10, 7), strollInsideWater ? entity -> true : entity -> !entity.isInWaterOrBubble());
    }

    public static BehaviorControl<PathfinderMob> stroll(float speed, int horizontalRadius, int verticalRadius) {
        return strollFlyOrSwim(speed, entity -> LandRandomPos.getPos(entity, horizontalRadius, verticalRadius), entity -> true);
    }

    public static BehaviorControl<PathfinderMob> fly(float speed) {
        return strollFlyOrSwim(speed, entity -> getTargetFlyPos(entity, 10, 7), entity -> true);
    }

    public static BehaviorControl<PathfinderMob> swim(float speed) {
        return strollFlyOrSwim(speed, RandomStroll::getTargetSwimPos, Entity::isInWaterOrBubble);
    }

    private static OneShot<PathfinderMob> strollFlyOrSwim(float speed, Function<PathfinderMob, Vec3> targetGetter, Predicate<PathfinderMob> shouldRun) {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.WALK_TARGET)).apply(context, walkTarget -> (world, entity, time) -> {
                        if (!shouldRun.test(entity)) {
                            return false;
                        } else {
                            Optional<Vec3> optional = Optional.ofNullable(targetGetter.apply(entity));
                            walkTarget.setOrErase(optional.map(pos -> new WalkTarget(pos, speed, 0)));
                            return true;
                        }
                    })
        );
    }

    @Nullable
    private static Vec3 getTargetSwimPos(PathfinderMob entity) {
        Vec3 vec3 = null;
        Vec3 vec32 = null;

        for (int[] is : SWIM_XY_DISTANCE_TIERS) {
            if (vec3 == null) {
                vec32 = BehaviorUtils.getRandomSwimmablePos(entity, is[0], is[1]);
            } else {
                vec32 = entity.position().add(entity.position().vectorTo(vec3).normalize().multiply((double)is[0], (double)is[1], (double)is[0]));
            }

            if (vec32 == null || entity.level().getFluidState(BlockPos.containing(vec32)).isEmpty()) {
                return vec3;
            }

            vec3 = vec32;
        }

        return vec32;
    }

    @Nullable
    private static Vec3 getTargetFlyPos(PathfinderMob entity, int horizontalRadius, int verticalRadius) {
        Vec3 vec3 = entity.getViewVector(0.0F);
        return AirAndWaterRandomPos.getPos(entity, horizontalRadius, verticalRadius, -2, vec3.x, vec3.z, (float) (Math.PI / 2));
    }
}
