package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class DimensionDataStorage implements java.io.Closeable { // Paper - Write SavedData IO async
    private static final Logger LOGGER = LogUtils.getLogger();
    public final Map<String, SavedData> cache = Maps.newHashMap();
    private final DataFixer fixerUpper;
    private final HolderLookup.Provider registries;
    private final File dataFolder;
    protected final java.util.concurrent.ExecutorService ioExecutor; // Paper - Write SavedData IO async

    public DimensionDataStorage(File directory, DataFixer dataFixer, HolderLookup.Provider registryLookup) {
        this.fixerUpper = dataFixer;
        this.dataFolder = directory;
        this.registries = registryLookup;
        this.ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(new com.google.common.util.concurrent.ThreadFactoryBuilder().setNameFormat("DimensionDataIO - " + dataFolder.getParent() + " - %d").setDaemon(true).build()); // Paper - Write SavedData IO async
    }

    private File getDataFile(String id) {
        return new File(this.dataFolder, id + ".dat");
    }

    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> type, String id) {
        T savedData = this.get(type, id);
        if (savedData != null) {
            return savedData;
        } else {
            T savedData2 = (T)type.constructor().get();
            this.set(id, savedData2);
            return savedData2;
        }
    }

    @Nullable
    public <T extends SavedData> T get(SavedData.Factory<T> type, String id) {
        SavedData savedData = this.cache.get(id);
        if (savedData == null && !this.cache.containsKey(id)) {
            savedData = this.readSavedData(type.deserializer(), type.type(), id);
            this.cache.put(id, savedData);
        }

        return (T)savedData;
    }

    @Nullable
    private <T extends SavedData> T readSavedData(BiFunction<CompoundTag, HolderLookup.Provider, T> readFunction, DataFixTypes dataFixTypes, String id) {
        try {
            File file = this.getDataFile(id);
            if (file.exists()) {
                CompoundTag compoundTag = this.readTagFromDisk(id, dataFixTypes, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                return readFunction.apply(compoundTag.getCompound("data"), this.registries);
            }
        } catch (Exception var6) {
            LOGGER.error("Error loading saved data: {}", id, var6);
        }

        return null;
    }

    public void set(String id, SavedData state) {
        this.cache.put(id, state);
    }

    public CompoundTag readTagFromDisk(String id, DataFixTypes dataFixTypes, int currentSaveVersion) throws IOException {
        File file = this.getDataFile(id);

        CompoundTag var9;
        try (
            InputStream inputStream = new FileInputStream(file);
            PushbackInputStream pushbackInputStream = new PushbackInputStream(new FastBufferedInputStream(inputStream), 2);
        ) {
            CompoundTag compoundTag;
            if (this.isGzip(pushbackInputStream)) {
                compoundTag = NbtIo.readCompressed(pushbackInputStream, NbtAccounter.unlimitedHeap());
            } else {
                try (DataInputStream dataInputStream = new DataInputStream(pushbackInputStream)) {
                    compoundTag = NbtIo.read(dataInputStream);
                }
            }

            int i = NbtUtils.getDataVersion(compoundTag, 1343);
            var9 = dataFixTypes.update(this.fixerUpper, compoundTag, i, currentSaveVersion);
        }

        return var9;
    }

    private boolean isGzip(PushbackInputStream stream) throws IOException {
        byte[] bs = new byte[2];
        boolean bl = false;
        int i = stream.read(bs, 0, 2);
        if (i == 2) {
            int j = (bs[1] & 255) << 8 | bs[0] & 255;
            if (j == 35615) {
                bl = true;
            }
        }

        if (i != 0) {
            stream.unread(bs, 0, i);
        }

        return bl;
    }

    // Paper start - Write SavedData IO async
    @Override
    public void close() throws IOException {
        save(false);
        this.ioExecutor.shutdown();
    }
    // Paper end - Write SavedData IO async

    public void save(boolean async) { // Paper - Write SavedData IO async
        this.cache.forEach((id, state) -> {
            if (state != null) {
                // Paper start - Write SavedData IO async
                final java.util.concurrent.CompletableFuture<Void> save = state.save(this.getDataFile(id), this.registries, this.ioExecutor);
                if (!async) {
                    save.join();
                }
                // Paper end - Write SavedData IO async
            }
        });
    }
}
