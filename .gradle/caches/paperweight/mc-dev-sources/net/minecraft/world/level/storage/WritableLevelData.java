package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;

public interface WritableLevelData extends LevelData {
    void setSpawn(BlockPos pos, float angle);
}
