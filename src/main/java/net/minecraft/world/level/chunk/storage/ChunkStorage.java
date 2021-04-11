package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
// CraftBukkit start
import java.util.concurrent.ExecutionException;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable {

    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    private final IOWorker worker;
    protected final DataFixer fixerUpper;
    @Nullable
    private volatile LegacyStructureDataHandler legacyStructureHandler;

    public ChunkStorage(RegionStorageInfo storageKey, Path directory, DataFixer dataFixer, boolean dsync) {
        this.fixerUpper = dataFixer;
        this.worker = new IOWorker(storageKey, directory, dsync);
    }

    public boolean isOldChunkAround(ChunkPos chunkPos, int checkRadius) {
        return this.worker.isOldChunkAround(chunkPos, checkRadius);
    }

    // CraftBukkit start
    private boolean check(ServerChunkCache cps, int x, int z) {
        if (true) return true; // Paper - Perf: this isn't even needed anymore, light is purged updating to 1.14+, why are we holding up the conversion process reading chunk data off disk - return true, we need to set light populated to true so the converter recognizes the chunk as being "full"
        ChunkPos pos = new ChunkPos(x, z);
        if (cps != null) {
            com.google.common.base.Preconditions.checkState(org.bukkit.Bukkit.isPrimaryThread(), "primary thread");
            if (cps.hasChunk(x, z)) {
                return true;
            }
        }

        CompoundTag nbt;
        try {
            nbt = this.read(pos).get().orElse(null);
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        if (nbt != null) {
            CompoundTag level = nbt.getCompound("Level");
            if (level.getBoolean("TerrainPopulated")) {
                return true;
            }

            ChunkStatus status = ChunkStatus.byName(level.getString("Status"));
            if (status != null && status.isOrAfter(ChunkStatus.FEATURES)) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag upgradeChunkTag(ResourceKey<LevelStem> resourcekey, Supplier<DimensionDataStorage> supplier, CompoundTag nbttagcompound, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> optional, ChunkPos pos, @Nullable LevelAccessor generatoraccess) {
        // CraftBukkit end
        int i = ChunkStorage.getVersion(nbttagcompound);

        if (i == SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            return nbttagcompound;
        } else {
            try {
                // CraftBukkit start
                if (i < 1466) {
                    CompoundTag level = nbttagcompound.getCompound("Level");
                    if (level.getBoolean("TerrainPopulated") && !level.getBoolean("LightPopulated")) {
                        ServerChunkCache cps = (generatoraccess == null) ? null : ((ServerLevel) generatoraccess).getChunkSource();
                        if (this.check(cps, pos.x - 1, pos.z) && this.check(cps, pos.x - 1, pos.z - 1) && this.check(cps, pos.x, pos.z - 1)) {
                            level.putBoolean("LightPopulated", true);
                        }
                    }
                }
                // CraftBukkit end

                if (i < 1493) {
                    nbttagcompound = DataFixTypes.CHUNK.update(this.fixerUpper, nbttagcompound, i, 1493);
                    if (nbttagcompound.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                        LegacyStructureDataHandler persistentstructurelegacy = this.getLegacyStructureHandler(resourcekey, supplier);

                        nbttagcompound = persistentstructurelegacy.updateFromLegacy(nbttagcompound);
                    }
                }

                // Spigot start - SPIGOT-6806: Quick and dirty way to prevent below zero generation in old chunks, by setting the status to heightmap instead of empty
                boolean stopBelowZero = false;
                boolean belowZeroGenerationInExistingChunks = (generatoraccess != null) ? ((ServerLevel) generatoraccess).spigotConfig.belowZeroGenerationInExistingChunks : org.spigotmc.SpigotConfig.belowZeroGenerationInExistingChunks;

                if (i <= 2730 && !belowZeroGenerationInExistingChunks) {
                    stopBelowZero = "full".equals(nbttagcompound.getCompound("Level").getString("Status"));
                }
                // Spigot end

                ChunkStorage.injectDatafixingContext(nbttagcompound, resourcekey, optional);
                nbttagcompound = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, nbttagcompound, Math.max(1493, i));
                // Spigot start
                if (stopBelowZero) {
                    nbttagcompound.putString("Status", net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(ChunkStatus.SPAWN).toString());
                }
                // Spigot end
                ChunkStorage.removeDatafixingContext(nbttagcompound);
                NbtUtils.addCurrentDataVersion(nbttagcompound);
                return nbttagcompound;
            } catch (Exception exception) {
                CrashReport crashreport = CrashReport.forThrowable(exception, "Updated chunk");
                CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Updated chunk details");

                crashreportsystemdetails.setDetail("Data version", (Object) i);
                throw new ReportedException(crashreport);
            }
        }
    }

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<LevelStem> worldKey, Supplier<DimensionDataStorage> stateManagerGetter) { // CraftBukkit
        LegacyStructureDataHandler persistentstructurelegacy = this.legacyStructureHandler;

        if (persistentstructurelegacy == null) {
            synchronized (this) {
                persistentstructurelegacy = this.legacyStructureHandler;
                if (persistentstructurelegacy == null) {
                    this.legacyStructureHandler = persistentstructurelegacy = LegacyStructureDataHandler.getLegacyStructureHandler(worldKey, (DimensionDataStorage) stateManagerGetter.get());
                }
            }
        }

        return persistentstructurelegacy;
    }

    public static void injectDatafixingContext(CompoundTag nbt, ResourceKey<LevelStem> worldKey, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> generatorCodecKey) { // CraftBukkit
        CompoundTag nbttagcompound1 = new CompoundTag();

        nbttagcompound1.putString("dimension", worldKey.location().toString());
        generatorCodecKey.ifPresent((resourcekey1) -> {
            nbttagcompound1.putString("generator", resourcekey1.location().toString());
        });
        nbt.put("__context", nbttagcompound1);
    }

    private static void removeDatafixingContext(CompoundTag nbt) {
        nbt.remove("__context");
    }

    public static int getVersion(CompoundTag nbt) {
        return NbtUtils.getDataVersion(nbt, -1);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos chunkPos) {
        return this.worker.loadAsync(chunkPos);
    }

    public CompletableFuture<Void> write(ChunkPos chunkPos, CompoundTag nbt) {
        // Paper start - guard against serializing mismatching coordinates
        if (nbt != null && !chunkPos.equals(ChunkSerializer.getChunkCoordinate(nbt))) {
            final String world = (this instanceof net.minecraft.server.level.ChunkMap) ? ((net.minecraft.server.level.ChunkMap) this).level.getWorld().getName() : null;
            throw new IllegalArgumentException("Chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + chunkPos
                + " but compound says coordinate is " + ChunkSerializer.getChunkCoordinate(nbt) + (world == null ? " for an unknown world" : (" for world: " + world)));
        }
        // Paper end - guard against serializing mismatching coordinates
        this.handleLegacyStructureIndex(chunkPos);
        return this.worker.store(chunkPos, nbt);
    }

    protected void handleLegacyStructureIndex(ChunkPos chunkPos) {
        if (this.legacyStructureHandler != null) {
            this.legacyStructureHandler.removeIndex(chunkPos.toLong());
        }

    }

    public void flushWorker() {
        this.worker.synchronize(true).join();
    }

    public void close() throws IOException {
        this.worker.close();
    }

    public ChunkScanAccess chunkScanner() {
        return this.worker;
    }

    protected RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
