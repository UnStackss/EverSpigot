package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class IOWorker implements ChunkScanAccess, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final ProcessorMailbox<StrictQueue.IntRunnable> mailbox;
    public final RegionFileStorage storage; // Paper - public
    private final Map<ChunkPos, IOWorker.PendingStore> pendingWrites = Maps.newLinkedHashMap();
    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<BitSet>> regionCacheForBlender = new Long2ObjectLinkedOpenHashMap<>();
    private static final int REGION_CACHE_SIZE = 1024;

    protected IOWorker(RegionStorageInfo storageKey, Path directory, boolean dsync) {
        this.storage = new RegionFileStorage(storageKey, directory, dsync);
        this.mailbox = new ProcessorMailbox<>(
            new StrictQueue.FixedPriorityQueue(IOWorker.Priority.values().length), Util.ioPool(), "IOWorker-" + storageKey.type()
        );
    }

    public boolean isOldChunkAround(ChunkPos chunkPos, int checkRadius) {
        ChunkPos chunkPos2 = new ChunkPos(chunkPos.x - checkRadius, chunkPos.z - checkRadius);
        ChunkPos chunkPos3 = new ChunkPos(chunkPos.x + checkRadius, chunkPos.z + checkRadius);

        for (int i = chunkPos2.getRegionX(); i <= chunkPos3.getRegionX(); i++) {
            for (int j = chunkPos2.getRegionZ(); j <= chunkPos3.getRegionZ(); j++) {
                BitSet bitSet = this.getOrCreateOldDataForRegion(i, j).join();
                if (!bitSet.isEmpty()) {
                    ChunkPos chunkPos4 = ChunkPos.minFromRegion(i, j);
                    int k = Math.max(chunkPos2.x - chunkPos4.x, 0);
                    int l = Math.max(chunkPos2.z - chunkPos4.z, 0);
                    int m = Math.min(chunkPos3.x - chunkPos4.x, 31);
                    int n = Math.min(chunkPos3.z - chunkPos4.z, 31);

                    for (int o = k; o <= m; o++) {
                        for (int p = l; p <= n; p++) {
                            int q = p * 32 + o;
                            if (bitSet.get(q)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private CompletableFuture<BitSet> getOrCreateOldDataForRegion(int chunkX, int chunkZ) {
        long l = ChunkPos.asLong(chunkX, chunkZ);
        synchronized (this.regionCacheForBlender) {
            CompletableFuture<BitSet> completableFuture = this.regionCacheForBlender.getAndMoveToFirst(l);
            if (completableFuture == null) {
                completableFuture = this.createOldDataForRegion(chunkX, chunkZ);
                this.regionCacheForBlender.putAndMoveToFirst(l, completableFuture);
                if (this.regionCacheForBlender.size() > 1024) {
                    this.regionCacheForBlender.removeLast();
                }
            }

            return completableFuture;
        }
    }

    private CompletableFuture<BitSet> createOldDataForRegion(int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(
            () -> {
                ChunkPos chunkPos = ChunkPos.minFromRegion(chunkX, chunkZ);
                ChunkPos chunkPos2 = ChunkPos.maxFromRegion(chunkX, chunkZ);
                BitSet bitSet = new BitSet();
                ChunkPos.rangeClosed(chunkPos, chunkPos2)
                    .forEach(
                        chunkPosx -> {
                            CollectFields collectFields = new CollectFields(
                                new FieldSelector(IntTag.TYPE, "DataVersion"), new FieldSelector(CompoundTag.TYPE, "blending_data")
                            );

                            try {
                                this.scanChunk(chunkPosx, collectFields).join();
                            } catch (Exception var7) {
                                LOGGER.warn("Failed to scan chunk {}", chunkPosx, var7);
                                return;
                            }

                            if (collectFields.getResult() instanceof CompoundTag compoundTag && this.isOldChunk(compoundTag)) {
                                int ix = chunkPosx.getRegionLocalZ() * 32 + chunkPosx.getRegionLocalX();
                                bitSet.set(ix);
                            }
                        }
                    );
                return bitSet;
            },
            Util.backgroundExecutor()
        );
    }

    private boolean isOldChunk(CompoundTag nbt) {
        return !nbt.contains("DataVersion", 99) || nbt.getInt("DataVersion") < 3441 || nbt.contains("blending_data", 10);
    }

    public CompletableFuture<Void> store(ChunkPos pos, @Nullable CompoundTag nbt) {
        return this.<CompletableFuture<Void>>submitTask(() -> {
            IOWorker.PendingStore pendingStore = this.pendingWrites.computeIfAbsent(pos, pos2 -> new IOWorker.PendingStore(nbt));
            pendingStore.data = nbt;
            return Either.left(pendingStore.result);
        }).thenCompose(Function.identity());
    }

    public CompletableFuture<Optional<CompoundTag>> loadAsync(ChunkPos pos) {
        return this.submitTask(() -> {
            IOWorker.PendingStore pendingStore = this.pendingWrites.get(pos);
            if (pendingStore != null) {
                return Either.left(Optional.ofNullable(pendingStore.copyData()));
            } else {
                try {
                    CompoundTag compoundTag = this.storage.read(pos);
                    return Either.left(Optional.ofNullable(compoundTag));
                } catch (Exception var4) {
                    LOGGER.warn("Failed to read chunk {}", pos, var4);
                    return Either.right(var4);
                }
            }
        });
    }

    public CompletableFuture<Void> synchronize(boolean sync) {
        CompletableFuture<Void> completableFuture = this.<CompletableFuture<Void>>submitTask(
                () -> Either.left(CompletableFuture.allOf(this.pendingWrites.values().stream().map(result -> result.result).toArray(CompletableFuture[]::new)))
            )
            .thenCompose(Function.identity());
        return sync ? completableFuture.thenCompose(void_ -> this.submitTask(() -> {
                try {
                    this.storage.flush();
                    return Either.left(null);
                } catch (Exception var2x) {
                    LOGGER.warn("Failed to synchronize chunks", (Throwable)var2x);
                    return Either.right(var2x);
                }
            })) : completableFuture.thenCompose(void_ -> this.submitTask(() -> Either.left(null)));
    }

    @Override
    public CompletableFuture<Void> scanChunk(ChunkPos pos, StreamTagVisitor scanner) {
        return this.submitTask(() -> {
            try {
                IOWorker.PendingStore pendingStore = this.pendingWrites.get(pos);
                if (pendingStore != null) {
                    if (pendingStore.data != null) {
                        pendingStore.data.acceptAsRoot(scanner);
                    }
                } else {
                    this.storage.scanChunk(pos, scanner);
                }

                return Either.left(null);
            } catch (Exception var4) {
                LOGGER.warn("Failed to bulk scan chunk {}", pos, var4);
                return Either.right(var4);
            }
        });
    }

    private <T> CompletableFuture<T> submitTask(Supplier<Either<T, Exception>> task) {
        return this.mailbox.askEither(listener -> new StrictQueue.IntRunnable(IOWorker.Priority.FOREGROUND.ordinal(), () -> {
                if (!this.shutdownRequested.get()) {
                    listener.tell(task.get());
                }

                this.tellStorePending();
            }));
    }

    private void storePendingChunk() {
        if (!this.pendingWrites.isEmpty()) {
            Iterator<Entry<ChunkPos, IOWorker.PendingStore>> iterator = this.pendingWrites.entrySet().iterator();
            Entry<ChunkPos, IOWorker.PendingStore> entry = iterator.next();
            iterator.remove();
            this.runStore(entry.getKey(), entry.getValue());
            this.tellStorePending();
        }
    }

    private void tellStorePending() {
        this.mailbox.tell(new StrictQueue.IntRunnable(IOWorker.Priority.BACKGROUND.ordinal(), this::storePendingChunk));
    }

    private void runStore(ChunkPos pos, IOWorker.PendingStore result) {
        try {
            this.storage.write(pos, result.data);
            result.result.complete(null);
        } catch (Exception var4) {
            LOGGER.error("Failed to store chunk {}", pos, var4);
            result.result.completeExceptionally(var4);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.shutdownRequested.compareAndSet(false, true)) {
            this.mailbox.ask(listener -> new StrictQueue.IntRunnable(IOWorker.Priority.SHUTDOWN.ordinal(), () -> listener.tell(Unit.INSTANCE))).join();
            this.mailbox.close();

            try {
                this.storage.close();
            } catch (Exception var2) {
                LOGGER.error("Failed to close storage", (Throwable)var2);
            }
        }
    }

    public RegionStorageInfo storageInfo() {
        return this.storage.info();
    }

    static class PendingStore {
        @Nullable
        CompoundTag data;
        final CompletableFuture<Void> result = new CompletableFuture<>();

        public PendingStore(@Nullable CompoundTag nbt) {
            this.data = nbt;
        }

        @Nullable
        CompoundTag copyData() {
            CompoundTag compoundTag = this.data;
            return compoundTag == null ? null : compoundTag.copy();
        }
    }

    static enum Priority {
        FOREGROUND,
        BACKGROUND,
        SHUTDOWN;
    }
}
