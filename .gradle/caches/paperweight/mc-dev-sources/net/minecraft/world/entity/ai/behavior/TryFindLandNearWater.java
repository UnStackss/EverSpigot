package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.apache.commons.lang3.mutable.MutableLong;

public class TryFindLandNearWater {
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
                                if (world.getFluidState(entity.blockPosition()).is(FluidTags.WATER)) {
                                    return false;
                                } else if (time < mutableLong.getValue()) {
                                    mutableLong.setValue(time + 40L);
                                    return true;
                                } else {
                                    CollisionContext collisionContext = CollisionContext.of(entity);
                                    BlockPos blockPos = entity.blockPosition();
                                    BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                                    label45:
                                    for (BlockPos blockPos2 : BlockPos.withinManhattan(blockPos, range, range, range)) {
                                        if ((blockPos2.getX() != blockPos.getX() || blockPos2.getZ() != blockPos.getZ())
                                            && world.getBlockState(blockPos2).getCollisionShape(world, blockPos2, collisionContext).isEmpty()
                                            && !world.getBlockState(mutableBlockPos.setWithOffset(blockPos2, Direction.DOWN))
                                                .getCollisionShape(world, blockPos2, collisionContext)
                                                .isEmpty()) {
                                            for (Direction direction : Direction.Plane.HORIZONTAL) {
                                                mutableBlockPos.setWithOffset(blockPos2, direction);
                                                if (world.getBlockState(mutableBlockPos).isAir()
                                                    && world.getBlockState(mutableBlockPos.move(Direction.DOWN)).is(Blocks.WATER)) {
                                                    lookTarget.set(new BlockPosTracker(blockPos2));
                                                    walkTarget.set(new WalkTarget(new BlockPosTracker(blockPos2), speed, 0));
                                                    break label45;
                                                }
                                            }
                                        }
                                    }

                                    mutableLong.setValue(time + 40L);
                                    return true;
                                }
                            }
                    )
        );
    }
}
