package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindLand {
    private static final int COOLDOWN_TICKS = 60;

    public static BehaviorControl<PathfinderMob> create(int range, float speed) {
        MutableLong mutableLong = new MutableLong(0L);
        return BehaviorBuilder.create(
            context -> context.group(
                        context.absent(MemoryModuleType.ATTACK_TARGET),
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET)
                    )
                    .apply(
                        context,
                        (attackTarget, walkTarget, lookTarget) -> (world, entity, time) -> {
                                if (!world.getFluidState(entity.blockPosition()).is(FluidTags.WATER)) {
                                    return false;
                                } else if (time < mutableLong.getValue()) {
                                    mutableLong.setValue(time + 60L);
                                    return true;
                                } else {
                                    BlockPos blockPos = entity.blockPosition();
                                    BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                                    CollisionContext collisionContext = CollisionContext.of(entity);

                                    for (BlockPos blockPos2 : BlockPos.withinManhattan(blockPos, range, range, range)) {
                                        if (blockPos2.getX() != blockPos.getX() || blockPos2.getZ() != blockPos.getZ()) {
                                            BlockState blockState = world.getBlockState(blockPos2);
                                            BlockState blockState2 = world.getBlockState(mutableBlockPos.setWithOffset(blockPos2, Direction.DOWN));
                                            if (!blockState.is(Blocks.WATER)
                                                && world.getFluidState(blockPos2).isEmpty()
                                                && blockState.getCollisionShape(world, blockPos2, collisionContext).isEmpty()
                                                && blockState2.isFaceSturdy(world, mutableBlockPos, Direction.UP)) {
                                                BlockPos blockPos3 = blockPos2.immutable();
                                                lookTarget.set(new BlockPosTracker(blockPos3));
                                                walkTarget.set(new WalkTarget(new BlockPosTracker(blockPos3), speed, 1));
                                                break;
                                            }
                                        }
                                    }

                                    mutableLong.setValue(time + 60L);
                                    return true;
                                }
                            }
                    )
        );
    }
}
