package net.minecraft.server.level.progress;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class StoringChunkProgressListener implements ChunkProgressListener {
    private final LoggerChunkProgressListener delegate;
    private final Long2ObjectOpenHashMap<ChunkStatus> statuses = new Long2ObjectOpenHashMap<>();
    private ChunkPos spawnPos = new ChunkPos(0, 0);
    private final int fullDiameter;
    private final int radius;
    private final int diameter;
    private boolean started;

    private StoringChunkProgressListener(LoggerChunkProgressListener progressLogger, int centerSize, int radius, int size) {
        this.delegate = progressLogger;
        this.fullDiameter = centerSize;
        this.radius = radius;
        this.diameter = size;
    }

    public static StoringChunkProgressListener createFromGameruleRadius(int spawnChunkRadius) {
        return spawnChunkRadius > 0 ? create(spawnChunkRadius + 1) : createCompleted();
    }

    public static StoringChunkProgressListener create(int spawnChunkRadius) {
        LoggerChunkProgressListener loggerChunkProgressListener = LoggerChunkProgressListener.create(spawnChunkRadius);
        int i = ChunkProgressListener.calculateDiameter(spawnChunkRadius);
        int j = spawnChunkRadius + ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        int k = ChunkProgressListener.calculateDiameter(j);
        return new StoringChunkProgressListener(loggerChunkProgressListener, i, j, k);
    }

    public static StoringChunkProgressListener createCompleted() {
        return new StoringChunkProgressListener(LoggerChunkProgressListener.createCompleted(), 0, 0, 0);
    }

    @Override
    public void updateSpawnPos(ChunkPos spawnPos) {
        if (this.started) {
            this.delegate.updateSpawnPos(spawnPos);
            this.spawnPos = spawnPos;
        }
    }

    @Override
    public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
        if (this.started) {
            this.delegate.onStatusChange(pos, status);
            if (status == null) {
                this.statuses.remove(pos.toLong());
            } else {
                this.statuses.put(pos.toLong(), status);
            }
        }
    }

    @Override
    public void start() {
        this.started = true;
        this.statuses.clear();
        this.delegate.start();
    }

    @Override
    public void stop() {
        this.started = false;
        this.delegate.stop();
    }

    public int getFullDiameter() {
        return this.fullDiameter;
    }

    public int getDiameter() {
        return this.diameter;
    }

    public int getProgress() {
        return this.delegate.getProgress();
    }

    @Nullable
    public ChunkStatus getStatus(int x, int z) {
        return this.statuses.get(ChunkPos.asLong(x + this.spawnPos.x - this.radius, z + this.spawnPos.z - this.radius));
    }
}
