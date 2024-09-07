package net.minecraft.world.level.pathfinder;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SwimNodeEvaluator extends NodeEvaluator {
    private final boolean allowBreaching;
    private final Long2ObjectMap<PathType> pathTypesByPosCache = new Long2ObjectOpenHashMap<>();

    public SwimNodeEvaluator(boolean canJumpOutOfWater) {
        this.allowBreaching = canJumpOutOfWater;
    }

    @Override
    public void prepare(PathNavigationRegion cachedWorld, Mob entity) {
        super.prepare(cachedWorld, entity);
        this.pathTypesByPosCache.clear();
    }

    @Override
    public void done() {
        super.done();
        this.pathTypesByPosCache.clear();
    }

    @Override
    public Node getStart() {
        return this.getNode(
            Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5), Mth.floor(this.mob.getBoundingBox().minZ)
        );
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] successors, Node node) {
        int i = 0;
        Map<Direction, Node> map = Maps.newEnumMap(Direction.class);

        for (Direction direction : Direction.values()) {
            Node node2 = this.findAcceptedNode(node.x + direction.getStepX(), node.y + direction.getStepY(), node.z + direction.getStepZ());
            map.put(direction, node2);
            if (this.isNodeValid(node2)) {
                successors[i++] = node2;
            }
        }

        for (Direction direction2 : Direction.Plane.HORIZONTAL) {
            Direction direction3 = direction2.getClockWise();
            if (hasMalus(map.get(direction2)) && hasMalus(map.get(direction3))) {
                Node node3 = this.findAcceptedNode(
                    node.x + direction2.getStepX() + direction3.getStepX(), node.y, node.z + direction2.getStepZ() + direction3.getStepZ()
                );
                if (this.isNodeValid(node3)) {
                    successors[i++] = node3;
                }
            }
        }

        return i;
    }

    protected boolean isNodeValid(@Nullable Node node) {
        return node != null && !node.closed;
    }

    private static boolean hasMalus(@Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    @Nullable
    protected Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        PathType pathType = this.getCachedBlockType(x, y, z);
        if (this.allowBreaching && pathType == PathType.BREACH || pathType == PathType.WATER) {
            float f = this.mob.getPathfindingMalus(pathType);
            if (f >= 0.0F) {
                node = this.getNode(x, y, z);
                node.type = pathType;
                node.costMalus = Math.max(node.costMalus, f);
                if (this.currentContext.level().getFluidState(new BlockPos(x, y, z)).isEmpty()) {
                    node.costMalus += 8.0F;
                }
            }
        }

        return node;
    }

    protected PathType getCachedBlockType(int x, int y, int z) {
        return this.pathTypesByPosCache.computeIfAbsent(BlockPos.asLong(x, y, z), pos -> this.getPathType(this.currentContext, x, y, z));
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return this.getPathTypeOfMob(context, x, y, z, this.mob);
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = x; i < x + this.entityWidth; i++) {
            for (int j = y; j < y + this.entityHeight; j++) {
                for (int k = z; k < z + this.entityDepth; k++) {
                    BlockState blockState = context.getBlockState(mutableBlockPos.set(i, j, k));
                    FluidState fluidState = blockState.getFluidState();
                    if (fluidState.isEmpty() && blockState.isPathfindable(PathComputationType.WATER) && blockState.isAir()) {
                        return PathType.BREACH;
                    }

                    if (!fluidState.is(FluidTags.WATER)) {
                        return PathType.BLOCKED;
                    }
                }
            }
        }

        BlockState blockState2 = context.getBlockState(mutableBlockPos);
        return blockState2.isPathfindable(PathComputationType.WATER) ? PathType.WATER : PathType.BLOCKED;
    }
}
