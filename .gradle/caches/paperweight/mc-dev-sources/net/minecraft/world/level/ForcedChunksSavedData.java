package net.minecraft.world.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class ForcedChunksSavedData extends SavedData {
    public static final String FILE_ID = "chunks";
    private static final String TAG_FORCED = "Forced";
    private final LongSet chunks;

    public static SavedData.Factory<ForcedChunksSavedData> factory() {
        return new SavedData.Factory<>(ForcedChunksSavedData::new, ForcedChunksSavedData::load, DataFixTypes.SAVED_DATA_FORCED_CHUNKS);
    }

    private ForcedChunksSavedData(LongSet chunks) {
        this.chunks = chunks;
    }

    public ForcedChunksSavedData() {
        this(new LongOpenHashSet());
    }

    public static ForcedChunksSavedData load(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        return new ForcedChunksSavedData(new LongOpenHashSet(nbt.getLongArray("Forced")));
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.putLongArray("Forced", this.chunks.toLongArray());
        return nbt;
    }

    public LongSet getChunks() {
        return this.chunks;
    }
}
