package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;

public class RecreatingChunkStorage extends ChunkStorage {
    private final IOWorker writeWorker;
    private final Path writeFolder;

    public RecreatingChunkStorage(
        RegionStorageInfo storageKey, Path directory, RegionStorageInfo outputStorageKey, Path outputDirectory, DataFixer dataFixer, boolean dsync
    ) {
        super(storageKey, directory, dataFixer, dsync);
        this.writeFolder = outputDirectory;
        this.writeWorker = new IOWorker(outputStorageKey, outputDirectory, dsync);
    }

    @Override
    public CompletableFuture<Void> write(ChunkPos chunkPos, CompoundTag nbt) {
        this.handleLegacyStructureIndex(chunkPos);
        return this.writeWorker.store(chunkPos, nbt);
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.writeWorker.close();
        if (this.writeFolder.toFile().exists()) {
            FileUtils.deleteDirectory(this.writeFolder.toFile());
        }
    }
}
