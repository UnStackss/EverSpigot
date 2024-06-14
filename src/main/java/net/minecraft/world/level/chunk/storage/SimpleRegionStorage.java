package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;

public class SimpleRegionStorage implements AutoCloseable {
    public final IOWorker worker; // Paper - public
    private final DataFixer fixerUpper;
    private final DataFixTypes dataFixType;

    public SimpleRegionStorage(RegionStorageInfo storageKey, Path directory, DataFixer dataFixer, boolean dsync, DataFixTypes dataFixTypes) {
        this.fixerUpper = dataFixer;
        this.dataFixType = dataFixTypes;
        this.worker = new IOWorker(storageKey, directory, dsync);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos pos) {
        return this.worker.loadAsync(pos);
    }

    public CompletableFuture<Void> write(ChunkPos pos, @Nullable CompoundTag nbt) {
        return this.worker.store(pos, nbt);
    }

    public CompoundTag upgradeChunkTag(CompoundTag nbt, int oldVersion) {
        int i = NbtUtils.getDataVersion(nbt, oldVersion);
        return this.dataFixType.updateToCurrentVersion(this.fixerUpper, nbt, i);
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> nbt, int oldVersion) {
        return this.dataFixType.updateToCurrentVersion(this.fixerUpper, nbt, oldVersion);
    }

    public CompletableFuture<Void> synchronize(boolean sync) {
        return this.worker.synchronize(sync);
    }

    @Override
    public void close() throws IOException {
        this.worker.close();
    }

    public RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
