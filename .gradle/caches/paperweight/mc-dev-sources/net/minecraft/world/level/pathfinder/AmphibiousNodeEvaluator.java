package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;

public class AmphibiousNodeEvaluator extends WalkNodeEvaluator {
    private final boolean prefersShallowSwimming;
    private float oldWalkableCost;
    private float oldWaterBorderCost;

    public AmphibiousNodeEvaluator(boolean penalizeDeepWater) {
        this.prefersShallowSwimming = penalizeDeepWater;
    }

    @Override
    public void prepare(PathNavigationRegion cachedWorld, Mob entity) {
        super.prepare(cachedWorld, entity);
        entity.setPathfindingMalus(PathType.WATER, 0.0F);
        this.oldWalkableCost = entity.getPathfindingMalus(PathType.WALKABLE);
        entity.setPathfindingMalus(PathType.WALKABLE, 6.0F);
        this.oldWaterBorderCost = entity.getPathfindingMalus(PathType.WATER_BORDER);
        entity.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public void done() {
        this.mob.setPathfindingMalus(PathType.WALKABLE, this.oldWalkableCost);
        this.mob.setPathfindingMalus(PathType.WATER_BORDER, this.oldWaterBorderCost);
        super.done();
    }

    @Override
    public Node getStart() {
        return !this.mob.isInWater()
            ? super.getStart()
            : this.getStartNode(
                new BlockPos(
                    Mth.floor(this.mob.getBoundingBox().minX), Mth.floor(this.mob.getBoundingBox().minY + 0.5), Mth.floor(this.mob.getBoundingBox().minZ)
                )
            );
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y + 0.5, z);
    }

    @Override
    public int getNeighbors(Node[] successors, Node node) {
        int i = super.getNeighbors(successors, node);
        PathType pathType = this.getCachedPathType(node.x, node.y + 1, node.z);
        PathType pathType2 = this.getCachedPathType(node.x, node.y, node.z);
        int j;
        if (this.mob.getPathfindingMalus(pathType) >= 0.0F && pathType2 != PathType.STICKY_HONEY) {
            j = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        } else {
            j = 0;
        }

        double d = this.getFloorLevel(new BlockPos(node.x, node.y, node.z));
        Node node2 = this.findAcceptedNode(node.x, node.y + 1, node.z, Math.max(0, j - 1), d, Direction.UP, pathType2);
        Node node3 = this.findAcceptedNode(node.x, node.y - 1, node.z, j, d, Direction.DOWN, pathType2);
        if (this.isVerticalNeighborValid(node2, node)) {
            successors[i++] = node2;
        }

        if (this.isVerticalNeighborValid(node3, node) && pathType2 != PathType.TRAPDOOR) {
            successors[i++] = node3;
        }

        for (int l = 0; l < i; l++) {
            Node node4 = successors[l];
            if (node4.type == PathType.WATER && this.prefersShallowSwimming && node4.y < this.mob.level().getSeaLevel() - 10) {
                node4.costMalus++;
            }
        }

        return i;
    }

    private boolean isVerticalNeighborValid(@Nullable Node node, Node successor) {
        return this.isNeighborValid(node, successor) && node.type == PathType.WATER;
    }

    @Override
    protected boolean isAmphibious() {
        return true;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType pathType = context.getPathTypeFromState(x, y, z);
        if (pathType == PathType.WATER) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Direction direction : Direction.values()) {
                mutableBlockPos.set(x, y, z).move(direction);
                PathType pathType2 = context.getPathTypeFromState(mutableBlockPos.getX(), mutableBlockPos.getY(), mutableBlockPos.getZ());
                if (pathType2 == PathType.BLOCKED) {
                    return PathType.WATER_BORDER;
                }
            }

            return PathType.WATER;
        } else {
            return super.getPathType(context, x, y, z);
        }
    }
}
