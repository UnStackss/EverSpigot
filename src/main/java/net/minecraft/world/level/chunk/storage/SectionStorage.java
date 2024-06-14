package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.slf4j.Logger;

public abstract class SectionStorage<R> implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.level.storage.ChunkSystemSectionStorage { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    // Paper - rewrite chunk system
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final Function<Runnable, Codec<R>> codec;
    private final Function<Runnable, R> factory;
    private final RegistryAccess registryAccess;
    private final ChunkIOErrorReporter errorReporter;
    protected final LevelHeightAccessor levelHeightAccessor;

    // Paper start - rewrite chunk system
    private final RegionFileStorage regionStorage;

    @Override
    public final RegionFileStorage moonrise$getRegionStorage() {
        return this.regionStorage;
    }
    // Paper end - rewrite chunk system

    public SectionStorage(
        SimpleRegionStorage storageAccess,
        Function<Runnable, Codec<R>> codecFactory,
        Function<Runnable, R> factory,
        RegistryAccess registryManager,
        ChunkIOErrorReporter errorHandler,
        LevelHeightAccessor world
    ) {
        // Paper - rewrite chunk system
        this.codec = codecFactory;
        this.factory = factory;
        this.registryAccess = registryManager;
        this.errorReporter = errorHandler;
        this.levelHeightAccessor = world;
        this.regionStorage = storageAccess.worker.storage; // Paper - rewrite chunk system
    }

    protected void tick(BooleanSupplier shouldKeepTicking) {
        while (this.hasWork() && shouldKeepTicking.getAsBoolean()) {
            ChunkPos chunkPos = SectionPos.of(this.dirty.firstLong()).chunk();
            this.writeColumn(chunkPos);
        }
    }

    public boolean hasWork() {
        return !this.dirty.isEmpty();
    }

    @Nullable
    protected Optional<R> get(long pos) {
        return this.storage.get(pos);
    }

    protected Optional<R> getOrLoad(long pos) {
        if (this.outsideStoredRange(pos)) {
            return Optional.empty();
        } else {
            Optional<R> optional = this.get(pos);
            if (optional != null) {
                return optional;
            } else {
                this.readColumn(SectionPos.of(pos).chunk());
                optional = this.get(pos);
                if (optional == null) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
                } else {
                    return optional;
                }
            }
        }
    }

    protected boolean outsideStoredRange(long pos) {
        int i = SectionPos.sectionToBlockCoord(SectionPos.y(pos));
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }

    protected R getOrCreate(long pos) {
        if (this.outsideStoredRange(pos)) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("sectionPos out of bounds"));
        } else {
            Optional<R> optional = this.getOrLoad(pos);
            if (optional.isPresent()) {
                return optional.get();
            } else {
                R object = this.factory.apply(() -> this.setDirty(pos));
                this.storage.put(pos, Optional.of(object));
                return object;
            }
        }
    }

    private void readColumn(ChunkPos pos) {
        Optional<CompoundTag> optional = this.tryRead(pos).join();
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        this.readColumn(pos, registryOps, optional.orElse(null));
    }

    private CompletableFuture<Optional<CompoundTag>> tryRead(ChunkPos pos) {
        // Paper start - rewrite chunk system
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.moonrise$read(pos.x, pos.z)));
        } catch (final Throwable thr) {
            return CompletableFuture.failedFuture(thr);
        }
        // Paper end - rewrite chunk system
    }

    private void readColumn(ChunkPos pos, RegistryOps<Tag> ops, @Nullable CompoundTag nbt) {
        throw new IllegalStateException("Only chunk system can load in state, offending class:" + this.getClass().getName()); // Paper - rewrite chunk system
    }

    private void writeColumn(ChunkPos pos) {
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> dynamic = this.writeColumn(pos, registryOps);
        Tag tag = dynamic.getValue();
        if (tag instanceof CompoundTag) {
            // Paper start - rewrite chunk system
            try {
                this.moonrise$write(pos.x, pos.z, (net.minecraft.nbt.CompoundTag)tag);
            } catch (final IOException ex) {
                LOGGER.error("Error writing poi chunk data to disk for chunk " + pos, ex);
            }
            // Paper end - rewrite chunk system
        } else {
            LOGGER.error("Expected compound tag, got {}", tag);
        }
    }

    private <T> Dynamic<T> writeColumn(ChunkPos chunkPos, DynamicOps<T> ops) {
        Map<T, T> map = Maps.newHashMap();

        for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
            long l = getKey(chunkPos, i);
            this.dirty.remove(l);
            Optional<R> optional = this.storage.get(l);
            if (optional != null && !optional.isEmpty()) {
                DataResult<T> dataResult = this.codec.apply(() -> this.setDirty(l)).encodeStart(ops, optional.get());
                String string = Integer.toString(i);
                dataResult.resultOrPartial(LOGGER::error).ifPresent(object -> map.put(ops.createString(string), (T)object));
            }
        }

        return new Dynamic<>(
            ops,
            ops.createMap(
                ImmutableMap.of(
                    ops.createString("Sections"),
                    ops.createMap(map),
                    ops.createString("DataVersion"),
                    ops.createInt(SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                )
            )
        );
    }

    private static long getKey(ChunkPos chunkPos, int y) {
        return SectionPos.asLong(chunkPos.x, y, chunkPos.z);
    }

    protected void onSectionLoad(long pos) {
    }

    public void setDirty(long pos) { // Paper - public
        Optional<R> optional = this.storage.get(pos);
        if (optional != null && !optional.isEmpty()) {
            this.dirty.add(pos);
        } else {
            LOGGER.warn("No data for position: {}", SectionPos.of(pos));
        }
    }

    private static int getVersion(Dynamic<?> dynamic) {
        return dynamic.get("DataVersion").asInt(1945);
    }

    public void flush(ChunkPos pos) {
        if (this.hasWork()) {
            for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
                long l = getKey(pos, i);
                if (this.dirty.contains(l)) {
                    this.writeColumn(pos);
                    return;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.moonrise$close(); // Paper - rewrite chunk system
    }
}
