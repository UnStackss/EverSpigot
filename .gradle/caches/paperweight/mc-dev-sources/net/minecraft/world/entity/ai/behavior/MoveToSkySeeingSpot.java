package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class MoveToSkySeeingSpot {
    public static OneShot<LivingEntity> create(float speed) {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.WALK_TARGET)).apply(context, walkTarget -> (world, entity, time) -> {
                        if (world.canSeeSky(entity.blockPosition())) {
                            return false;
                        } else {
                            Optional<Vec3> optional = Optional.ofNullable(getOutdoorPosition(world, entity));
                            optional.ifPresent(pos -> walkTarget.set(new WalkTarget(pos, speed, 0)));
                            return true;
                        }
                    })
        );
    }

    @Nullable
    private static Vec3 getOutdoorPosition(ServerLevel world, LivingEntity entity) {
        RandomSource randomSource = entity.getRandom();
        BlockPos blockPos = entity.blockPosition();

        for (int i = 0; i < 10; i++) {
            BlockPos blockPos2 = blockPos.offset(randomSource.nextInt(20) - 10, randomSource.nextInt(6) - 3, randomSource.nextInt(20) - 10);
            if (hasNoBlocksAbove(world, entity, blockPos2)) {
                return Vec3.atBottomCenterOf(blockPos2);
            }
        }

        return null;
    }

    public static boolean hasNoBlocksAbove(ServerLevel world, LivingEntity entity, BlockPos pos) {
        return world.canSeeSky(pos) && (double)world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= entity.getY();
    }
}
