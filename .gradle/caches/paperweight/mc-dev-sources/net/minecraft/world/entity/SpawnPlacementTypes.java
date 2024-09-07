package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public interface SpawnPlacementTypes {
    SpawnPlacementType NO_RESTRICTIONS = (world, pos, entityType) -> true;
    SpawnPlacementType IN_WATER = (world, pos, entityType) -> {
        if (entityType != null && world.getWorldBorder().isWithinBounds(pos)) {
            BlockPos blockPos = pos.above();
            return world.getFluidState(pos).is(FluidTags.WATER) && !world.getBlockState(blockPos).isRedstoneConductor(world, blockPos);
        } else {
            return false;
        }
    };
    SpawnPlacementType IN_LAVA = (world, pos, entityType) -> entityType != null
            && world.getWorldBorder().isWithinBounds(pos)
            && world.getFluidState(pos).is(FluidTags.LAVA);
    SpawnPlacementType ON_GROUND = new SpawnPlacementType() {
        @Override
        public boolean isSpawnPositionOk(LevelReader world, BlockPos pos, @Nullable EntityType<?> entityType) {
            if (entityType != null && world.getWorldBorder().isWithinBounds(pos)) {
                BlockPos blockPos = pos.above();
                BlockPos blockPos2 = pos.below();
                BlockState blockState = world.getBlockState(blockPos2);
                return blockState.isValidSpawn(world, blockPos2, entityType)
                    && this.isValidEmptySpawnBlock(world, pos, entityType)
                    && this.isValidEmptySpawnBlock(world, blockPos, entityType);
            } else {
                return false;
            }
        }

        private boolean isValidEmptySpawnBlock(LevelReader world, BlockPos pos, EntityType<?> entityType) {
            BlockState blockState = world.getBlockState(pos);
            return NaturalSpawner.isValidEmptySpawnBlock(world, pos, blockState, blockState.getFluidState(), entityType);
        }

        @Override
        public BlockPos adjustSpawnPosition(LevelReader world, BlockPos pos) {
            BlockPos blockPos = pos.below();
            return world.getBlockState(blockPos).isPathfindable(PathComputationType.LAND) ? blockPos : pos;
        }
    };
}
