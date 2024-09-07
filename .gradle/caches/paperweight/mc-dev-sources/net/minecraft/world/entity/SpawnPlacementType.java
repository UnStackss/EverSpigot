package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

public interface SpawnPlacementType {
    boolean isSpawnPositionOk(LevelReader world, BlockPos pos, @Nullable EntityType<?> entityType);

    default BlockPos adjustSpawnPosition(LevelReader world, BlockPos pos) {
        return pos;
    }
}
