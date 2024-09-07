package net.minecraft.world.entity.ai.behavior;

import java.util.Collections;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InsideBrownianWalk {
    public static BehaviorControl<PathfinderMob> create(float speed) {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.WALK_TARGET))
                    .apply(
                        context,
                        walkTarget -> (world, entity, time) -> {
                                if (world.canSeeSky(entity.blockPosition())) {
                                    return false;
                                } else {
                                    BlockPos blockPos = entity.blockPosition();
                                    List<BlockPos> list = BlockPos.betweenClosedStream(blockPos.offset(-1, -1, -1), blockPos.offset(1, 1, 1))
                                        .map(BlockPos::immutable)
                                        .collect(Util.toMutableList());
                                    Collections.shuffle(list);
                                    list.stream()
                                        .filter(pos -> !world.canSeeSky(pos))
                                        .filter(pos -> world.loadedAndEntityCanStandOn(pos, entity))
                                        .filter(pos -> world.noCollision(entity))
                                        .findFirst()
                                        .ifPresent(pos -> walkTarget.set(new WalkTarget(pos, speed, 0)));
                                    return true;
                                }
                            }
                    )
        );
    }
}
