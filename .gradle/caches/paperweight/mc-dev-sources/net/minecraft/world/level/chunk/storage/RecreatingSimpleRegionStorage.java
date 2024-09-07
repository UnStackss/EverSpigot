package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;

public class RecreatingSimpleRegionStorage extends SimpleRegionStorage {
    private final IOWorker writeWorker;
    private final Path writeFolder;

    public RecreatingSimpleRegionStorage(
        RegionStorageInfo storageKey,
        Path directory,
        RegionStorageInfo outputStorageKey,
        Path outputDirectory,
        DataFixer dataFixer,
        boolean dsync,
        DataFixTypes dataFixTypes
    ) {
        super(storageKey, directory, dataFixer, dsync, dataFixTypes);
        this.writeFolder = outputDirectory;
        this.writeWorker = new IOWorker(outputStorageKey, outputDirectory, dsync);
    }

    @Override
    public CompletableFuture<Void> write(ChunkPos pos, @Nullable CompoundTag nbt) {
        return this.writeWorker.store(pos, nbt);
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
