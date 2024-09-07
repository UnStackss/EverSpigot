package net.minecraft.world.level.levelgen.structure;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class StructureFeatureIndexSavedData extends SavedData {
    private static final String TAG_REMAINING_INDEXES = "Remaining";
    private static final String TAG_All_INDEXES = "All";
    private final LongSet all;
    private final LongSet remaining;

    public static SavedData.Factory<StructureFeatureIndexSavedData> factory() {
        return new SavedData.Factory<>(
            StructureFeatureIndexSavedData::new, StructureFeatureIndexSavedData::load, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES
        );
    }

    private StructureFeatureIndexSavedData(LongSet all, LongSet remaining) {
        this.all = all;
        this.remaining = remaining;
    }

    public StructureFeatureIndexSavedData() {
        this(new LongOpenHashSet(), new LongOpenHashSet());
    }

    public static StructureFeatureIndexSavedData load(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        return new StructureFeatureIndexSavedData(new LongOpenHashSet(nbt.getLongArray("All")), new LongOpenHashSet(nbt.getLongArray("Remaining")));
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.putLongArray("All", this.all.toLongArray());
        nbt.putLongArray("Remaining", this.remaining.toLongArray());
        return nbt;
    }

    public void addIndex(long pos) {
        this.all.add(pos);
        this.remaining.add(pos);
    }

    public boolean hasStartIndex(long pos) {
        return this.all.contains(pos);
    }

    public boolean hasUnhandledIndex(long pos) {
        return this.remaining.contains(pos);
    }

    public void removeIndex(long pos) {
        this.remaining.remove(pos);
    }

    public LongSet getAll() {
        return this.all;
    }
}
