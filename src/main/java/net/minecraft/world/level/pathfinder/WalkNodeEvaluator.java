package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WalkNodeEvaluator extends NodeEvaluator {
    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;
    private final Long2ObjectMap<PathType> pathTypesByPosCacheByMob = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<AABB> collisionCache = new Object2BooleanOpenHashMap<>();
    private final Node[] reusableNeighbors = new Node[Direction.Plane.HORIZONTAL.length()];

    @Override
    public void prepare(PathNavigationRegion cachedWorld, Mob entity) {
        super.prepare(cachedWorld, entity);
        entity.onPathfindingStart();
    }

    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypesByPosCacheByMob.clear();
        this.collisionCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i = this.mob.getBlockY();
        BlockState blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), (double)i, this.mob.getZ()));
        if (!this.mob.canStandOnFluid(blockState.getFluidState())) {
            if (this.canFloat() && this.mob.isInWater()) {
                while (true) {
                    if (!blockState.is(Blocks.WATER) && blockState.getFluidState() != Fluids.WATER.getSource(false)) {
                        i--;
                        break;
                    }

                    blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), (double)(++i), this.mob.getZ()));
                }
            } else if (this.mob.onGround()) {
                i = Mth.floor(this.mob.getY() + 0.5);
            } else {
                mutableBlockPos.set(this.mob.getX(), this.mob.getY() + 1.0, this.mob.getZ());

                while (mutableBlockPos.getY() > this.currentContext.level().getMinBuildHeight()) {
                    i = mutableBlockPos.getY();
                    mutableBlockPos.setY(mutableBlockPos.getY() - 1);
                    BlockState blockState2 = this.currentContext.getBlockState(mutableBlockPos);
                    if (!blockState2.isAir() && !blockState2.isPathfindable(PathComputationType.LAND)) {
                        break;
                    }
                }
            }
        } else {
            while (this.mob.canStandOnFluid(blockState.getFluidState())) {
                blockState = this.currentContext.getBlockState(mutableBlockPos.set(this.mob.getX(), (double)(++i), this.mob.getZ()));
            }

            i--;
        }

        BlockPos blockPos = this.mob.blockPosition();
        if (!this.canStartAt(mutableBlockPos.set(blockPos.getX(), i, blockPos.getZ()))) {
            AABB aABB = this.mob.getBoundingBox();
            if (this.canStartAt(mutableBlockPos.set(aABB.minX, (double)i, aABB.minZ))
                || this.canStartAt(mutableBlockPos.set(aABB.minX, (double)i, aABB.maxZ))
                || this.canStartAt(mutableBlockPos.set(aABB.maxX, (double)i, aABB.minZ))
                || this.canStartAt(mutableBlockPos.set(aABB.maxX, (double)i, aABB.maxZ))) {
                return this.getStartNode(mutableBlockPos);
            }
        }

        return this.getStartNode(new BlockPos(blockPos.getX(), i, blockPos.getZ()));
    }

    protected Node getStartNode(BlockPos pos) {
        Node node = this.getNode(pos);
        node.type = this.getCachedPathType(node.x, node.y, node.z);
        node.costMalus = this.mob.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(BlockPos pos) {
        PathType pathType = this.getCachedPathType(pos.getX(), pos.getY(), pos.getZ());
        return pathType != PathType.OPEN && this.mob.getPathfindingMalus(pathType) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] successors, Node node) {
        int i = 0;
        int j = 0;
        PathType pathType = this.getCachedPathType(node.x, node.y + 1, node.z);
        PathType pathType2 = this.getCachedPathType(node.x, node.y, node.z);
        if (this.mob.getPathfindingMalus(pathType) >= 0.0F && pathType2 != PathType.STICKY_HONEY) {
            j = Mth.floor(Math.max(1.0F, this.mob.maxUpStep()));
        }

        double d = this.getFloorLevel(new BlockPos(node.x, node.y, node.z));

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Node node2 = this.findAcceptedNode(node.x + direction.getStepX(), node.y, node.z + direction.getStepZ(), j, d, direction, pathType2);
            this.reusableNeighbors[direction.get2DDataValue()] = node2;
            if (this.isNeighborValid(node2, node)) {
                successors[i++] = node2;
            }
        }

        for (Direction direction2 : Direction.Plane.HORIZONTAL) {
            Direction direction3 = direction2.getClockWise();
            if (this.isDiagonalValid(node, this.reusableNeighbors[direction2.get2DDataValue()], this.reusableNeighbors[direction3.get2DDataValue()])) {
                Node node3 = this.findAcceptedNode(
                    node.x + direction2.getStepX() + direction3.getStepX(),
                    node.y,
                    node.z + direction2.getStepZ() + direction3.getStepZ(),
                    j,
                    d,
                    direction2,
                    pathType2
                );
                if (this.isDiagonalValid(node3)) {
                    successors[i++] = node3;
                }
            }
        }

        return i;
    }

    protected boolean isNeighborValid(@Nullable Node node, Node successor) {
        return node != null && !node.closed && (node.costMalus >= 0.0F || successor.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(Node xNode, @Nullable Node zNode, @Nullable Node xDiagNode) {
        if (xDiagNode == null || zNode == null || xDiagNode.y > xNode.y || zNode.y > xNode.y) {
            return false;
        } else if (zNode.type != PathType.WALKABLE_DOOR && xDiagNode.type != PathType.WALKABLE_DOOR) {
            boolean bl = xDiagNode.type == PathType.FENCE && zNode.type == PathType.FENCE && (double)this.mob.getBbWidth() < 0.5;
            return (xDiagNode.y < xNode.y || xDiagNode.costMalus >= 0.0F || bl) && (zNode.y < xNode.y || zNode.costMalus >= 0.0F || bl);
        } else {
            return false;
        }
    }

    protected boolean isDiagonalValid(@Nullable Node node) {
        return node != null && !node.closed && node.type != PathType.WALKABLE_DOOR && node.costMalus >= 0.0F;
    }

    private static boolean doesBlockHavePartialCollision(PathType nodeType) {
        return nodeType == PathType.FENCE || nodeType == PathType.DOOR_WOOD_CLOSED || nodeType == PathType.DOOR_IRON_CLOSED;
    }

    private boolean canReachWithoutCollision(Node node) {
        AABB aABB = this.mob.getBoundingBox();
        Vec3 vec3 = new Vec3(
            (double)node.x - this.mob.getX() + aABB.getXsize() / 2.0,
            (double)node.y - this.mob.getY() + aABB.getYsize() / 2.0,
            (double)node.z - this.mob.getZ() + aABB.getZsize() / 2.0
        );
        int i = Mth.ceil(vec3.length() / aABB.getSize());
        vec3 = vec3.scale((double)(1.0F / (float)i));

        for (int j = 1; j <= i; j++) {
            aABB = aABB.move(vec3);
            if (this.hasCollisions(aABB)) {
                return false;
            }
        }

        return true;
    }

    protected double getFloorLevel(BlockPos pos) {
        BlockGetter blockGetter = this.currentContext.level();
        return (this.canFloat() || this.isAmphibious()) && blockGetter.getFluidState(pos).is(FluidTags.WATER)
            ? (double)pos.getY() + 0.5
            : getFloorLevel(blockGetter, pos);
    }

    public static double getFloorLevel(BlockGetter world, BlockPos pos) {
        BlockPos blockPos = pos.below();
        VoxelShape voxelShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
        return (double)blockPos.getY() + (voxelShape.isEmpty() ? 0.0 : voxelShape.max(Direction.Axis.Y));
    }

    protected boolean isAmphibious() {
        return false;
    }

    @Nullable
    protected Node findAcceptedNode(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, PathType nodeType) {
        Node node = null;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        double d = this.getFloorLevel(mutableBlockPos.set(x, y, z));
        if (d - prevFeetY > this.getMobJumpHeight()) {
            return null;
        } else {
            PathType pathType = this.getCachedPathType(x, y, z);
            float f = this.mob.getPathfindingMalus(pathType);
            if (f >= 0.0F) {
                node = this.getNodeAndUpdateCostToMax(x, y, z, pathType, f);
            }

            if (doesBlockHavePartialCollision(nodeType) && node != null && node.costMalus >= 0.0F && !this.canReachWithoutCollision(node)) {
                node = null;
            }

            if (pathType != PathType.WALKABLE && (!this.isAmphibious() || pathType != PathType.WATER)) {
                if ((node == null || node.costMalus < 0.0F)
                    && maxYStep > 0
                    && (pathType != PathType.FENCE || this.canWalkOverFences())
                    && pathType != PathType.UNPASSABLE_RAIL
                    && pathType != PathType.TRAPDOOR
                    && pathType != PathType.POWDER_SNOW) {
                    node = this.tryJumpOn(x, y, z, maxYStep, prevFeetY, direction, nodeType, mutableBlockPos);
                } else if (!this.isAmphibious() && pathType == PathType.WATER && !this.canFloat()) {
                    node = this.tryFindFirstNonWaterBelow(x, y, z, node);
                } else if (pathType == PathType.OPEN) {
                    node = this.tryFindFirstGroundNodeBelow(x, y, z);
                } else if (doesBlockHavePartialCollision(pathType) && node == null) {
                    node = this.getClosedNode(x, y, z, pathType);
                }

                return node;
            } else {
                return node;
            }
        }
    }

    private double getMobJumpHeight() {
        return Math.max(1.125, (double)this.mob.maxUpStep());
    }

    private Node getNodeAndUpdateCostToMax(int x, int y, int z, PathType type, float penalty) {
        Node node = this.getNode(x, y, z);
        node.type = type;
        node.costMalus = Math.max(node.costMalus, penalty);
        return node;
    }

    private Node getBlockedNode(int x, int y, int z) {
        Node node = this.getNode(x, y, z);
        node.type = PathType.BLOCKED;
        node.costMalus = -1.0F;
        return node;
    }

    private Node getClosedNode(int x, int y, int z, PathType type) {
        Node node = this.getNode(x, y, z);
        node.closed = true;
        node.type = type;
        node.costMalus = type.getMalus();
        return node;
    }

    @Nullable
    private Node tryJumpOn(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, PathType nodeType, BlockPos.MutableBlockPos mutablePos) {
        Node node = this.findAcceptedNode(x, y + 1, z, maxYStep - 1, prevFeetY, direction, nodeType);
        if (node == null) {
            return null;
        } else if (this.mob.getBbWidth() >= 1.0F) {
            return node;
        } else if (node.type != PathType.OPEN && node.type != PathType.WALKABLE) {
            return node;
        } else {
            double d = (double)(x - direction.getStepX()) + 0.5;
            double e = (double)(z - direction.getStepZ()) + 0.5;
            double f = (double)this.mob.getBbWidth() / 2.0;
            AABB aABB = new AABB(
                d - f,
                this.getFloorLevel(mutablePos.set(d, (double)(y + 1), e)) + 0.001,
                e - f,
                d + f,
                (double)this.mob.getBbHeight() + this.getFloorLevel(mutablePos.set((double)node.x, (double)node.y, (double)node.z)) - 0.002,
                e + f
            );
            return this.hasCollisions(aABB) ? null : node;
        }
    }

    @Nullable
    private Node tryFindFirstNonWaterBelow(int x, int y, int z, @Nullable Node node) {
        y--;

        while (y > this.mob.level().getMinBuildHeight()) {
            PathType pathType = this.getCachedPathType(x, y, z);
            if (pathType != PathType.WATER) {
                return node;
            }

            node = this.getNodeAndUpdateCostToMax(x, y, z, pathType, this.mob.getPathfindingMalus(pathType));
            y--;
        }

        return node;
    }

    private Node tryFindFirstGroundNodeBelow(int x, int y, int z) {
        for (int i = y - 1; i >= this.mob.level().getMinBuildHeight(); i--) {
            if (y - i > this.mob.getMaxFallDistance()) {
                return this.getBlockedNode(x, i, z);
            }

            PathType pathType = this.getCachedPathType(x, i, z);
            float f = this.mob.getPathfindingMalus(pathType);
            if (pathType != PathType.OPEN) {
                if (f >= 0.0F) {
                    return this.getNodeAndUpdateCostToMax(x, i, z, pathType, f);
                }

                return this.getBlockedNode(x, i, z);
            }
        }

        return this.getBlockedNode(x, y, z);
    }

    private boolean hasCollisions(AABB box) {
        return this.collisionCache.computeIfAbsent(box, box2 -> !this.currentContext.level().noCollision(this.mob, box));
    }

    protected PathType getCachedPathType(int x, int y, int z) {
        return this.pathTypesByPosCacheByMob.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob));
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        Set<PathType> set = this.getPathTypeWithinMobBB(context, x, y, z);
        if (set.contains(PathType.FENCE)) {
            return PathType.FENCE;
        } else if (set.contains(PathType.UNPASSABLE_RAIL)) {
            return PathType.UNPASSABLE_RAIL;
        } else {
            PathType pathType = PathType.BLOCKED;

            for (PathType pathType2 : set) {
                if (mob.getPathfindingMalus(pathType2) < 0.0F) {
                    return pathType2;
                }

                if (mob.getPathfindingMalus(pathType2) >= mob.getPathfindingMalus(pathType)) {
                    pathType = pathType2;
                }
            }

            return this.entityWidth <= 1
                    && pathType != PathType.OPEN
                    && mob.getPathfindingMalus(pathType) == 0.0F
                    && this.getPathType(context, x, y, z) == PathType.OPEN
                ? PathType.OPEN
                : pathType;
        }
    }

    public Set<PathType> getPathTypeWithinMobBB(PathfindingContext context, int x, int y, int z) {
        EnumSet<PathType> enumSet = EnumSet.noneOf(PathType.class);

        for (int i = 0; i < this.entityWidth; i++) {
            for (int j = 0; j < this.entityHeight; j++) {
                for (int k = 0; k < this.entityDepth; k++) {
                    int l = i + x;
                    int m = j + y;
                    int n = k + z;
                    PathType pathType = this.getPathType(context, l, m, n);
                    BlockPos blockPos = this.mob.blockPosition();
                    boolean bl = this.canPassDoors();
                    if (pathType == PathType.DOOR_WOOD_CLOSED && this.canOpenDoors() && bl) {
                        pathType = PathType.WALKABLE_DOOR;
                    }

                    if (pathType == PathType.DOOR_OPEN && !bl) {
                        pathType = PathType.BLOCKED;
                    }

                    if (pathType == PathType.RAIL
                        && this.getPathType(context, blockPos.getX(), blockPos.getY(), blockPos.getZ()) != PathType.RAIL
                        && this.getPathType(context, blockPos.getX(), blockPos.getY() - 1, blockPos.getZ()) != PathType.RAIL) {
                        pathType = PathType.UNPASSABLE_RAIL;
                    }

                    enumSet.add(pathType);
                }
            }
        }

        return enumSet;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return getPathTypeStatic(context, new BlockPos.MutableBlockPos(x, y, z));
    }

    public static PathType getPathTypeStatic(Mob entity, BlockPos pos) {
        return getPathTypeStatic(new PathfindingContext(entity.level(), entity), pos.mutable());
    }

    public static PathType getPathTypeStatic(PathfindingContext context, BlockPos.MutableBlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        PathType pathType = context.getPathTypeFromState(i, j, k);
        if (pathType == PathType.OPEN && j >= context.level().getMinBuildHeight() + 1) {
            return switch (context.getPathTypeFromState(i, j - 1, k)) {
                case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
                case DAMAGE_FIRE -> PathType.DAMAGE_FIRE;
                case DAMAGE_OTHER -> PathType.DAMAGE_OTHER;
                case STICKY_HONEY -> PathType.STICKY_HONEY;
                case POWDER_SNOW -> PathType.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> PathType.DAMAGE_CAUTIOUS;
                case TRAPDOOR -> PathType.DANGER_TRAPDOOR;
                default -> checkNeighbourBlocks(context, i, j, k, PathType.WALKABLE);
            };
        } else {
            return pathType;
        }
    }

    public static PathType checkNeighbourBlocks(PathfindingContext context, int x, int y, int z, PathType fallback) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    if (i != 0 || k != 0) {
                        PathType pathType = context.getPathTypeFromState(x + i, y + j, z + k);
                        if (pathType == PathType.DAMAGE_OTHER) {
                            return PathType.DANGER_OTHER;
                        }

                        if (pathType == PathType.DAMAGE_FIRE || pathType == PathType.LAVA) {
                            return PathType.DANGER_FIRE;
                        }

                        if (pathType == PathType.WATER) {
                            return PathType.WATER_BORDER;
                        }

                        if (pathType == PathType.DAMAGE_CAUTIOUS) {
                            return PathType.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }

        return fallback;
    }

    protected static PathType getPathTypeFromState(BlockGetter world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();
        if (blockState.isAir()) {
            return PathType.OPEN;
        } else if (blockState.is(BlockTags.TRAPDOORS) || blockState.is(Blocks.LILY_PAD) || blockState.is(Blocks.BIG_DRIPLEAF)) {
            return PathType.TRAPDOOR;
        } else if (blockState.is(Blocks.POWDER_SNOW)) {
            return PathType.POWDER_SNOW;
        } else if (blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
            return PathType.DAMAGE_OTHER;
        } else if (blockState.is(Blocks.HONEY_BLOCK)) {
            return PathType.STICKY_HONEY;
        } else if (blockState.is(Blocks.COCOA)) {
            return PathType.COCOA;
        } else if (!blockState.is(Blocks.WITHER_ROSE) && !blockState.is(Blocks.POINTED_DRIPSTONE)) {
            FluidState fluidState = blockState.getFluidState();
            if (fluidState.is(FluidTags.LAVA)) {
                return PathType.LAVA;
            } else if (isBurningBlock(blockState)) {
                return PathType.DAMAGE_FIRE;
            } else if (block instanceof DoorBlock doorBlock) {
                if (blockState.getValue(DoorBlock.OPEN)) {
                    return PathType.DOOR_OPEN;
                } else {
                    return doorBlock.type().canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
                }
            } else if (block instanceof BaseRailBlock) {
                return PathType.RAIL;
            } else if (block instanceof LeavesBlock) {
                return PathType.LEAVES;
            } else if (!blockState.is(BlockTags.FENCES)
                && !blockState.is(BlockTags.WALLS)
                && (!(block instanceof FenceGateBlock) || blockState.getValue(FenceGateBlock.OPEN))) {
                if (!blockState.isPathfindable(PathComputationType.LAND)) {
                    return PathType.BLOCKED;
                } else {
                    return fluidState.is(FluidTags.WATER) ? PathType.WATER : PathType.OPEN;
                }
            } else {
                return PathType.FENCE;
            }
        } else {
            return PathType.DAMAGE_CAUTIOUS;
        }
    }
}
