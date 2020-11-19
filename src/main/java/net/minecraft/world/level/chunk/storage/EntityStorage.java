package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import org.slf4j.Logger;

public class EntityStorage implements EntityPersistentStorage<Entity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTITIES_TAG = "Entities";
    private static final String POSITION_TAG = "Position";
    public final ServerLevel level;
    private final SimpleRegionStorage simpleRegionStorage;
    private final LongSet emptyChunks = new LongOpenHashSet();
    public final ProcessorMailbox<Runnable> entityDeserializerQueue;

    public EntityStorage(SimpleRegionStorage storage, ServerLevel world, Executor executor) {
        this.simpleRegionStorage = storage;
        this.level = world;
        this.entityDeserializerQueue = ProcessorMailbox.create(executor, "entity-deserializer");
    }

    @Override
    public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) {
        if (this.emptyChunks.contains(pos.toLong())) {
            return CompletableFuture.completedFuture(emptyChunk(pos));
        } else {
            CompletableFuture<Optional<CompoundTag>> completableFuture = this.simpleRegionStorage.read(pos);
            this.reportLoadFailureIfPresent(completableFuture, pos);
            return completableFuture.thenApplyAsync(nbt -> {
                if (nbt.isEmpty()) {
                    this.emptyChunks.add(pos.toLong());
                    return emptyChunk(pos);
                } else {
                    try {
                        ChunkPos chunkPos2 = readChunkPos(nbt.get());
                        if (!Objects.equals(pos, chunkPos2)) {
                            LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", pos, pos, chunkPos2);
                            this.level.getServer().reportMisplacedChunk(chunkPos2, pos, this.simpleRegionStorage.storageInfo());
                        }
                    } catch (Exception var6) {
                        LOGGER.warn("Failed to parse chunk {} position info", pos, var6);
                        this.level.getServer().reportChunkLoadFailure(var6, this.simpleRegionStorage.storageInfo(), pos);
                    }

                    CompoundTag compoundTag = this.simpleRegionStorage.upgradeChunkTag(nbt.get(), -1);
                    ListTag listTag = compoundTag.getList("Entities", 10);
                    List<Entity> list = EntityType.loadEntitiesRecursive(listTag, this.level).collect(ImmutableList.toImmutableList());
                    return new ChunkEntities<>(pos, list);
                }
            }, this.entityDeserializerQueue::tell);
        }
    }

    public static ChunkPos readChunkPos(CompoundTag chunkNbt) { // Paper - public
        int[] is = chunkNbt.getIntArray("Position");
        return new ChunkPos(is[0], is[1]);
    }

    public static void writeChunkPos(CompoundTag chunkNbt, ChunkPos pos) { // Paper - public
        chunkNbt.put("Position", new IntArrayTag(new int[]{pos.x, pos.z}));
    }

    private static ChunkEntities<Entity> emptyChunk(ChunkPos pos) {
        return new ChunkEntities<>(pos, ImmutableList.of());
    }

    @Override
    public void storeEntities(ChunkEntities<Entity> dataList) {
        ChunkPos chunkPos = dataList.getPos();
        if (dataList.isEmpty()) {
            if (this.emptyChunks.add(chunkPos.toLong())) {
                this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkPos, null), chunkPos);
            }
        } else {
            ListTag listTag = new ListTag();
            final java.util.Map<net.minecraft.world.entity.EntityType<?>, Integer> savedEntityCounts = new java.util.HashMap<>(); // Paper - Entity load/save limit per chunk
            dataList.getEntities().forEach(entity -> {
                // Paper start - Entity load/save limit per chunk
                final EntityType<?> entityType = entity.getType();
                final int saveLimit = this.level.paperConfig().chunks.entityPerChunkSaveLimit.getOrDefault(entityType, -1);
                if (saveLimit > -1) {
                    if (savedEntityCounts.getOrDefault(entityType, 0) >= saveLimit) {
                        return;
                    }
                    savedEntityCounts.merge(entityType, 1, Integer::sum);
                }
                // Paper end - Entity load/save limit per chunk
                CompoundTag compoundTagx = new CompoundTag();
                if (entity.save(compoundTagx)) {
                    listTag.add(compoundTagx);
                }
            });
            CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
            compoundTag.put("Entities", listTag);
            writeChunkPos(compoundTag, chunkPos);
            this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkPos, compoundTag), chunkPos);
            this.emptyChunks.remove(chunkPos.toLong());
        }
    }

    private void reportSaveFailureIfPresent(CompletableFuture<?> future, ChunkPos pos) {
        future.exceptionally(throwable -> {
            LOGGER.error("Failed to store entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    private void reportLoadFailureIfPresent(CompletableFuture<?> future, ChunkPos pos) {
        future.exceptionally(throwable -> {
            LOGGER.error("Failed to load entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkLoadFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    @Override
    public void flush(boolean sync) {
        this.simpleRegionStorage.synchronize(sync).join();
        this.entityDeserializerQueue.runAll();
    }

    @Override
    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }
}
