package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public interface LightEventListener {
    void checkBlock(BlockPos pos);

    boolean hasLightWork();

    int runLightUpdates();

    default void updateSectionStatus(BlockPos pos, boolean notReady) {
        this.updateSectionStatus(SectionPos.of(pos), notReady);
    }

    void updateSectionStatus(SectionPos pos, boolean notReady);

    void setLightEnabled(ChunkPos pos, boolean retainData);

    void propagateLightSources(ChunkPos chunkPos);
}
