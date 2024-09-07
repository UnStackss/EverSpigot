package net.minecraft.server.level.progress;

import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ChunkProgressListener {
    void updateSpawnPos(ChunkPos spawnPos);

    void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status);

    void start();

    void stop();

    static int calculateDiameter(int spawnChunkRadius) {
        return 2 * spawnChunkRadius + 1;
    }
}
