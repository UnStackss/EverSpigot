package net.minecraft.world.level.saveddata;

import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import org.slf4j.Logger;

public abstract class SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean dirty;

    public abstract CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup);

    public void setDirty() {
        this.setDirty(true);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    // Paper start - Write SavedData IO async - joining is evil, but we assume the old blocking behavior here just for safety
    @io.papermc.paper.annotation.DoNotUse
    public void save(File file, HolderLookup.Provider registryLookup) {
        save(file, registryLookup, null).join();
    }

    public java.util.concurrent.CompletableFuture<Void> save(File file, HolderLookup.Provider registryLookup, @org.jetbrains.annotations.Nullable java.util.concurrent.ExecutorService ioExecutor) {
        // Paper end - Write SavedData IO async
        if (this.isDirty()) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("data", this.save(new CompoundTag(), registryLookup));
            NbtUtils.addCurrentDataVersion(compoundTag);

            Runnable writeRunnable = () -> { // Paper - Write SavedData IO async
            try {
                NbtIo.writeCompressed(compoundTag, file.toPath());
            } catch (IOException var5) {
                LOGGER.error("Could not save data {}", this, var5);
            }
            }; // Paper - Write SavedData IO async

            this.setDirty(false);
            // Paper start - Write SavedData IO async
            if (ioExecutor == null) {
                return java.util.concurrent.CompletableFuture.runAsync(writeRunnable); // No executor, just use common pool
            }
            return java.util.concurrent.CompletableFuture.runAsync(writeRunnable, ioExecutor);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
        // Paper end - Write SavedData IO async
    }

    public static record Factory<T extends SavedData>(
        Supplier<T> constructor, BiFunction<CompoundTag, HolderLookup.Provider, T> deserializer, DataFixTypes type
    ) {
    }
}
