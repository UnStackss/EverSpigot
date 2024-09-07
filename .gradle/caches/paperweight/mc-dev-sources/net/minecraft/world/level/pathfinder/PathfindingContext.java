package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;

public class PathfindingContext {
    private final CollisionGetter level;
    @Nullable
    private final PathTypeCache cache;
    private final BlockPos mobPosition;
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public PathfindingContext(CollisionGetter world, Mob entity) {
        this.level = world;
        if (entity.level() instanceof ServerLevel serverLevel) {
            this.cache = serverLevel.getPathTypeCache();
        } else {
            this.cache = null;
        }

        this.mobPosition = entity.blockPosition();
    }

    public PathType getPathTypeFromState(int x, int y, int z) {
        BlockPos blockPos = this.mutablePos.set(x, y, z);
        return this.cache == null ? WalkNodeEvaluator.getPathTypeFromState(this.level, blockPos) : this.cache.getOrCompute(this.level, blockPos);
    }

    public BlockState getBlockState(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    public CollisionGetter level() {
        return this.level;
    }

    public BlockPos mobPosition() {
        return this.mobPosition;
    }
}
