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

public final class RegionFileStorage implements AutoCloseable {

    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    RegionFileStorage(RegionStorageInfo storageKey, Path directory, boolean dsync) {
        this.folder = directory;
        this.sync = dsync;
        this.info = storageKey;
    }

    private RegionFile getRegionFile(ChunkPos chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit
        long i = ChunkPos.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ());
        RegionFile regionfile = (RegionFile) this.regionCache.getAndMoveToFirst(i);

        if (regionfile != null) {
            return regionfile;
        } else {
            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper - Sanitise RegionFileCache and make configurable
                ((RegionFile) this.regionCache.removeLast()).close();
            }

            FileUtil.createDirectoriesSafe(this.folder);
            Path path = this.folder;
            int j = chunkcoordintpair.getRegionX();
            Path path1 = path.resolve("r." + j + "." + chunkcoordintpair.getRegionZ() + ".mca");
            if (existingOnly && !java.nio.file.Files.exists(path1)) return null; // CraftBukkit
            RegionFile regionfile1 = new RegionFile(this.info, path1, this.folder, this.sync);

            this.regionCache.putAndMoveToFirst(i, regionfile1);
            return regionfile1;
        }
    }

    @Nullable
    public CompoundTag read(ChunkPos pos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        RegionFile regionfile = this.getRegionFile(pos, true);
        if (regionfile == null) {
            return null;
        }
        // CraftBukkit end
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(pos);

        CompoundTag nbttagcompound;
        label43:
        {
            try {
                if (datainputstream != null) {
                    nbttagcompound = NbtIo.read((DataInput) datainputstream);
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

    protected void write(ChunkPos pos, @Nullable CompoundTag nbt) throws IOException {
        RegionFile regionfile = this.getRegionFile(pos, false); // CraftBukkit
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
        ExceptionCollector<IOException> exceptionsuppressor = new ExceptionCollector<>();
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptionsuppressor.add(ioexception);
            }
        }

        exceptionsuppressor.throwIfPresent();
    }

    public void flush() throws IOException {
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            RegionFile regionfile = (RegionFile) objectiterator.next();

            regionfile.flush();
        }

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
