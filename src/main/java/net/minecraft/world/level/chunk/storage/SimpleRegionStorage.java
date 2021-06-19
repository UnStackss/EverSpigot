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

    // Paper start - rewrite data conversion system
    private ca.spottedleaf.dataconverter.minecraft.datatypes.MCDataType getDataConverterType() {
        if (this.dataFixType == DataFixTypes.ENTITY_CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.ENTITY_CHUNK;
        } else if (this.dataFixType == DataFixTypes.POI_CHUNK) {
            return ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.POI_CHUNK;
        } else {
            throw new UnsupportedOperationException("For " + this.dataFixType.name());
        }
    }
    // Paper end - rewrite data conversion system

    public CompoundTag upgradeChunkTag(CompoundTag nbt, int oldVersion) {
        // Paper start - rewrite data conversion system
        final int dataVer = NbtUtils.getDataVersion(nbt, oldVersion);
        return ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(this.getDataConverterType(), nbt, dataVer, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        // Paper end - rewrite data conversion system
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> nbt, int oldVersion) {
        // Paper start - rewrite data conversion system
        final CompoundTag converted = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(this.getDataConverterType(), (CompoundTag)nbt.getValue(), oldVersion, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return new Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, converted);
        // Paper end - rewrite data conversion system
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
