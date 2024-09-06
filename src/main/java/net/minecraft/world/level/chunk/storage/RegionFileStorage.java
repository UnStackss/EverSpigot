package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

public class RegionFileStorage implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage { // Paper - rewrite chunk system

    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    // Paper start - rewrite chunk system
    private static final int REGION_SHIFT = 5;
    private static final int MAX_NON_EXISTING_CACHE = 1024 * 64;
    private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet nonExistingRegionFiles = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet(MAX_NON_EXISTING_CACHE+1);
    private static String getRegionFileName(final int chunkX, final int chunkZ) {
        return "r." + (chunkX >> REGION_SHIFT) + "." + (chunkZ >> REGION_SHIFT) + ".mca";
    }

    private boolean doesRegionFilePossiblyExist(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.contains(position)) {
                this.nonExistingRegionFiles.addAndMoveToFirst(position);
                return false;
            }
            return true;
        }
    }

    private void createRegionFile(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            this.nonExistingRegionFiles.remove(position);
        }
    }

    private void markNonExisting(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
                while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                    this.nonExistingRegionFiles.removeLastLong();
                }
            }
        }
    }

    @Override
    public final boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ) {
        return !this.doesRegionFilePossiblyExist(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ) {
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {
        final long key = ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);

        RegionFile ret = this.regionCache.getAndMoveToFirst(key);
        if (ret != null) {
            return ret;
        }

        if (!this.doesRegionFilePossiblyExist(key)) {
            return null;
        }

        if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
            this.regionCache.removeLast().close();
        }

        final Path regionPath = this.folder.resolve(getRegionFileName(chunkX, chunkZ));

        if (!java.nio.file.Files.exists(regionPath)) {
            this.markNonExisting(key);
            return null;
        }

        this.createRegionFile(key);

        FileUtil.createDirectoriesSafe(this.folder);

        ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

        this.regionCache.putAndMoveToFirst(key, ret);

        return ret;
    }
    // Paper end - rewrite chunk system
    // Paper start - recalculate region file headers
    private final boolean isChunkData;

    public static boolean isChunkDataFolder(Path path) {
        return path.toFile().getName().equalsIgnoreCase("region");
    }

    @Nullable
    public static ChunkPos getRegionFileCoordinates(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) {
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x << 5, z << 5);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    // Paper end

    protected RegionFileStorage(RegionStorageInfo storageKey, Path directory, boolean dsync) { // Paper - protected
        this.folder = directory;
        this.sync = dsync;
        this.info = storageKey;
        this.isChunkData = isChunkDataFolder(this.folder); // Paper - recalculate region file headers
    }

    public RegionFile getRegionFile(ChunkPos chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit // Paper - public
        // Paper start - rewrite chunk system
        if (existingOnly) {
            return this.moonrise$getRegionFileIfExists(chunkcoordintpair.x, chunkcoordintpair.z);
        }
        synchronized (this) {
            final long key = ChunkPos.asLong(chunkcoordintpair.x >> REGION_SHIFT, chunkcoordintpair.z >> REGION_SHIFT);

            RegionFile ret = this.regionCache.getAndMoveToFirst(key);
            if (ret != null) {
                return ret;
            }

            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
                this.regionCache.removeLast().close();
            }

            final Path regionPath = this.folder.resolve(getRegionFileName(chunkcoordintpair.x, chunkcoordintpair.z));

            this.createRegionFile(key);

            FileUtil.createDirectoriesSafe(this.folder);

            ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(key, ret);

            return ret;
        }
        // Paper end - rewrite chunk system
    }

    // Paper start
    private static void printOversizedLog(String msg, Path file, int x, int z) {
        org.apache.logging.log4j.LogManager.getLogger().fatal(msg + " (" + file.toString().replaceAll(".+[\\\\/]", "") + " - " + x + "," + z + ") Go clean it up to remove this message. /minecraft:tp " + (x<<4)+" 128 "+(z<<4) + " - DO NOT REPORT THIS TO PAPER - You may ask for help on Discord, but do not file an issue. These error messages can not be removed.");
    }

    private static CompoundTag readOversizedChunk(RegionFile regionfile, ChunkPos chunkCoordinate) throws IOException {
        synchronized (regionfile) {
            try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkCoordinate)) {
                CompoundTag oversizedData = regionfile.getOversizedData(chunkCoordinate.x, chunkCoordinate.z);
                CompoundTag chunk = NbtIo.read((DataInput) datainputstream);
                if (oversizedData == null) {
                    return chunk;
                }
                CompoundTag oversizedLevel = oversizedData.getCompound("Level");

                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "Entities", "Entities");
                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "TileEntities", "TileEntities");

                return chunk;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
        }
    }

    private static void mergeChunkList(CompoundTag level, CompoundTag oversizedLevel, String key, String oversizedKey) {
        net.minecraft.nbt.ListTag levelList = level.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag oversizedList = oversizedLevel.getList(oversizedKey, net.minecraft.nbt.Tag.TAG_COMPOUND);

        if (!oversizedList.isEmpty()) {
            levelList.addAll(oversizedList);
            level.put(key, levelList);
        }
    }
    // Paper end

    @Nullable
    public CompoundTag read(ChunkPos pos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(pos, true);
        if (regionfile == null) {
            return null;
        }
        // CraftBukkit end
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(pos);

        // Paper start
        if (regionfile.isOversized(pos.x, pos.z)) {
            printOversizedLog("Loading Oversized Chunk!", regionfile.getPath(), pos.x, pos.z);
            return readOversizedChunk(regionfile, pos);
        }
        // Paper end
        CompoundTag nbttagcompound;
        label43:
        {
            try {
                if (datainputstream != null) {
                    nbttagcompound = NbtIo.read((DataInput) datainputstream);
                    // Paper start - recover from corrupt regionfile header
                    if (this.isChunkData) {
                        ChunkPos chunkPos = ChunkSerializer.getChunkCoordinate(nbttagcompound);
                        if (!chunkPos.equals(pos)) {
                            net.minecraft.server.MinecraftServer.LOGGER.error("Attempting to read chunk data at " + pos + " but got chunk data for " + chunkPos + " instead! Attempting regionfile recalculation for regionfile " + regionfile.getPath().toAbsolutePath());
                            if (regionfile.recalculateHeader()) {
                                return this.read(pos);
                            }
                            net.minecraft.server.MinecraftServer.LOGGER.error("Can't recalculate regionfile header, regenerating chunk " + pos + " for " + regionfile.getPath().toAbsolutePath());
                            return null;
                        }
                    }
                    // Paper end - recover from corrupt regionfile header
                    break label43;
                }

                nbttagcompound = null;
            } catch (Throwable throwable) {
                if (datainputstream != null) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }

            if (datainputstream != null) {
                datainputstream.close();
            }

            return nbttagcompound;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

        return nbttagcompound;
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor scanner) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(chunkPos, true);
        if (regionfile == null) {
            return;
        }
        // CraftBukkit end
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkPos);

        try {
            if (datainputstream != null) {
                NbtIo.parse(datainputstream, scanner, NbtAccounter.unlimitedHeap());
            }
        } catch (Throwable throwable) {
            if (datainputstream != null) {
                try {
                    datainputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

    }

    public void write(ChunkPos pos, @Nullable CompoundTag nbt) throws IOException { // Paper - public
        RegionFile regionfile = this.getRegionFile(pos, nbt == null); // CraftBukkit // Paper - rewrite chunk system
        // Paper start - rewrite chunk system
        if (regionfile == null) {
            // if the RegionFile doesn't exist, no point in deleting from it
            return;
        }
        // Paper end - rewrite chunk system
        // Paper start - Chunk save reattempt
        int attempts = 0;
        Exception lastException = null;
        while (attempts++ < 5) { try {
        // Paper end - Chunk save reattempt

        if (nbt == null) {
            regionfile.clear(pos);
        } else {
            DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(pos);

            try {
                NbtIo.write(nbt, (DataOutput) dataoutputstream);
                regionfile.setOversized(pos.x, pos.z, false); // Paper - We don't do this anymore, mojang stores differently, but clear old meta flag if it exists to get rid of our own meta file once last oversized is gone
                // Paper start - don't write garbage data to disk if writing serialization fails
                dataoutputstream.close(); // Only write if successful
            } catch (final RegionFileSizeException e) {
                attempts = 5; // Don't retry
                regionfile.clear(pos);
                throw e;
                // Paper end - don't write garbage data to disk if writing serialization fails
            } catch (Throwable throwable) {
                if (dataoutputstream != null) {
                    try {
                        //dataoutputstream.close(); // Paper - don't write garbage data to disk if writing serialization fails
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }
            // Paper - don't write garbage data to disk if writing serialization fails; move into try block to only write if successfully serialized
        }
        // Paper start - Chunk save reattempt
                return;
            } catch (Exception ex)  {
                lastException = ex;
            }
        }

        if (lastException != null) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(lastException);
            net.minecraft.server.MinecraftServer.LOGGER.error("Failed to save chunk {}", pos, lastException);
        }
        // Paper end - Chunk save reattempt
    }

    public void close() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final RegionFile regionFile : this.regionCache.values()) {
                try {
                    regionFile.close();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public void flush() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            final ExceptionCollector<IOException> exceptionCollector = new ExceptionCollector<>();
            for (final RegionFile regionFile : this.regionCache.values()) {
                try {
                    regionFile.flush();
                } catch (final IOException ex) {
                    exceptionCollector.add(ex);
                }
            }

            exceptionCollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system

    }

    public RegionStorageInfo info() {
        return this.info;
    }

    // Paper start - don't write garbage data to disk if writing serialization fails
    public static final class RegionFileSizeException extends RuntimeException {

        public RegionFileSizeException(String message) {
            super(message);
        }
    }
    // Paper end - don't write garbage data to disk if writing serialization fails
}
