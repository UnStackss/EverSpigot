package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public abstract class NodeEvaluator {
    protected PathfindingContext currentContext;
    protected Mob mob;
    protected final Int2ObjectMap<Node> nodes = new Int2ObjectOpenHashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;
    protected boolean canPassDoors;
    protected boolean canOpenDoors;
    protected boolean canFloat;
    protected boolean canWalkOverFences;

    public void prepare(PathNavigationRegion cachedWorld, Mob entity) {
        this.currentContext = new PathfindingContext(cachedWorld, entity);
        this.mob = entity;
        this.nodes.clear();
        this.entityWidth = Mth.floor(entity.getBbWidth() + 1.0F);
        this.entityHeight = Mth.floor(entity.getBbHeight() + 1.0F);
        this.entityDepth = Mth.floor(entity.getBbWidth() + 1.0F);
    }

    public void done() {
        this.currentContext = null;
        this.mob = null;
    }

    protected Node getNode(BlockPos pos) {
        return this.getNode(pos.getX(), pos.getY(), pos.getZ());
    }

    protected Node getNode(int x, int y, int z) {
        return this.nodes.computeIfAbsent(Node.createHash(x, y, z), l -> new Node(x, y, z));
    }

    public abstract Node getStart();

    public abstract Target getTarget(double x, double y, double z);

    protected Target getTargetNodeAt(double x, double y, double z) {
        return new Target(this.getNode(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
    }

    public abstract int getNeighbors(Node[] successors, Node node);

    public abstract PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob);

    public abstract PathType getPathType(PathfindingContext context, int x, int y, int z);

    public PathType getPathType(Mob entity, BlockPos pos) {
        return this.getPathType(new PathfindingContext(entity.level(), entity), pos.getX(), pos.getY(), pos.getZ());
    }

    public void setCanPassDoors(boolean canEnterOpenDoors) {
        this.canPassDoors = canEnterOpenDoors;
    }

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.canOpenDoors = canOpenDoors;
    }

    public void setCanFloat(boolean canSwim) {
        this.canFloat = canSwim;
    }

    public void setCanWalkOverFences(boolean canWalkOverFences) {
        this.canWalkOverFences = canWalkOverFences;
    }

    public boolean canPassDoors() {
        return this.canPassDoors;
    }

    public boolean canOpenDoors() {
        return this.canOpenDoors;
    }

    public boolean canFloat() {
        return this.canFloat;
    }

    public boolean canWalkOverFences() {
        return this.canWalkOverFences;
    }

    public static boolean isBurningBlock(BlockState state) {
        return state.is(BlockTags.FIRE)
            || state.is(Blocks.LAVA)
            || state.is(Blocks.MAGMA_BLOCK)
            || CampfireBlock.isLitCampfire(state)
            || state.is(Blocks.LAVA_CAULDRON);
    }
}
