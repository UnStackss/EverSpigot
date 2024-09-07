package net.minecraft.world.level.chunk.storage;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.world.level.ChunkPos;

public interface ChunkIOErrorReporter {
    void reportChunkLoadFailure(Throwable exception, RegionStorageInfo key, ChunkPos chunkPos);

    void reportChunkSaveFailure(Throwable exception, RegionStorageInfo key, ChunkPos chunkPos);

    static ReportedException createMisplacedChunkReport(ChunkPos actualPos, ChunkPos expectedPos) {
        CrashReport crashReport = CrashReport.forThrowable(
            new IllegalStateException("Retrieved chunk position " + actualPos + " does not match requested " + expectedPos), "Chunk found in invalid location"
        );
        CrashReportCategory crashReportCategory = crashReport.addCategory("Misplaced Chunk");
        crashReportCategory.setDetail("Stored Position", actualPos::toString);
        return new ReportedException(crashReport);
    }

    default void reportMisplacedChunk(ChunkPos actualPos, ChunkPos expectedPos, RegionStorageInfo key) {
        this.reportChunkLoadFailure(createMisplacedChunkReport(actualPos, expectedPos), key, expectedPos);
    }
}
