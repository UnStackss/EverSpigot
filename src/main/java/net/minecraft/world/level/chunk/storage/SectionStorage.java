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

public class SectionStorage<R> implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    private final SimpleRegionStorage simpleRegionStorage;
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final Function<Runnable, Codec<R>> codec;
    private final Function<Runnable, R> factory;
    private final RegistryAccess registryAccess;
    private final ChunkIOErrorReporter errorReporter;
    protected final LevelHeightAccessor levelHeightAccessor;

    public SectionStorage(
        SimpleRegionStorage storageAccess,
        Function<Runnable, Codec<R>> codecFactory,
        Function<Runnable, R> factory,
        RegistryAccess registryManager,
        ChunkIOErrorReporter errorHandler,
        LevelHeightAccessor world
    ) {
        this.simpleRegionStorage = storageAccess;
        this.codec = codecFactory;
        this.factory = factory;
        this.registryAccess = registryManager;
        this.errorReporter = errorHandler;
        this.levelHeightAccessor = world;
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
        return this.simpleRegionStorage.read(pos).exceptionally(throwable -> {
            if (throwable instanceof IOException iOException) {
                LOGGER.error("Error reading chunk {} data from disk", pos, iOException);
                this.errorReporter.reportChunkLoadFailure(iOException, this.simpleRegionStorage.storageInfo(), pos);
                return Optional.empty();
            } else {
                throw new CompletionException(throwable);
            }
        });
    }

    private void readColumn(ChunkPos pos, RegistryOps<Tag> ops, @Nullable CompoundTag nbt) {
        if (nbt == null) {
            for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
                this.storage.put(getKey(pos, i), Optional.empty());
            }
        } else {
            Dynamic<Tag> dynamic = new Dynamic<>(ops, nbt);
            int j = getVersion(dynamic);
            int k = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            boolean bl = j != k;
            Dynamic<Tag> dynamic2 = this.simpleRegionStorage.upgradeChunkTag(dynamic, j);
            OptionalDynamic<Tag> optionalDynamic = dynamic2.get("Sections");

            for (int l = this.levelHeightAccessor.getMinSection(); l < this.levelHeightAccessor.getMaxSection(); l++) {
                long m = getKey(pos, l);
                Optional<R> optional = optionalDynamic.get(Integer.toString(l))
                    .result()
                    .flatMap(dynamicx -> this.codec.apply(() -> this.setDirty(m)).parse(dynamicx).resultOrPartial(LOGGER::error));
                this.storage.put(m, optional);
                optional.ifPresent(sections -> {
                    this.onSectionLoad(m);
                    if (bl) {
                        this.setDirty(m);
                    }
                });
            }
        }
    }

    private void writeColumn(ChunkPos pos) {
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> dynamic = this.writeColumn(pos, registryOps);
        Tag tag = dynamic.getValue();
        if (tag instanceof CompoundTag) {
            this.simpleRegionStorage.write(pos, (CompoundTag)tag).exceptionally(throwable -> {
                this.errorReporter.reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
                return null;
            });
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

    protected void setDirty(long pos) {
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
        this.simpleRegionStorage.close();
    }
}
