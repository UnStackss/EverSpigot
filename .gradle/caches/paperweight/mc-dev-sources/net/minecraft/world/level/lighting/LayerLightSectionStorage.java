package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> {
    private final LightLayer layer;
    protected final LightChunkGetter chunkSource;
    protected final Long2ByteMap sectionStates = new Long2ByteOpenHashMap();
    private final LongSet columnsWithSources = new LongOpenHashSet();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final LongSet changedSections = new LongOpenHashSet();
    protected final LongSet sectionsAffectedByLightUpdates = new LongOpenHashSet();
    protected final Long2ObjectMap<DataLayer> queuedSections = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final LongSet columnsToRetainQueuedDataFor = new LongOpenHashSet();
    private final LongSet toRemove = new LongOpenHashSet();
    protected volatile boolean hasInconsistencies;

    protected LayerLightSectionStorage(LightLayer lightType, LightChunkGetter chunkProvider, M lightData) {
        this.layer = lightType;
        this.chunkSource = chunkProvider;
        this.updatingSectionData = lightData;
        this.visibleSectionData = lightData.copy();
        this.visibleSectionData.disableCache();
        this.sectionStates.defaultReturnValue((byte)0);
    }

    protected boolean storingLightForSection(long sectionPos) {
        return this.getDataLayer(sectionPos, true) != null;
    }

    @Nullable
    protected DataLayer getDataLayer(long sectionPos, boolean cached) {
        return this.getDataLayer(cached ? this.updatingSectionData : this.visibleSectionData, sectionPos);
    }

    @Nullable
    protected DataLayer getDataLayer(M storage, long sectionPos) {
        return storage.getLayer(sectionPos);
    }

    @Nullable
    protected DataLayer getDataLayerToWrite(long sectionPos) {
        DataLayer dataLayer = this.updatingSectionData.getLayer(sectionPos);
        if (dataLayer == null) {
            return null;
        } else {
            if (this.changedSections.add(sectionPos)) {
                dataLayer = dataLayer.copy();
                this.updatingSectionData.setLayer(sectionPos, dataLayer);
                this.updatingSectionData.clearCache();
            }

            return dataLayer;
        }
    }

    @Nullable
    public DataLayer getDataLayerData(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        return dataLayer != null ? dataLayer : this.getDataLayer(sectionPos, false);
    }

    protected abstract int getLightValue(long blockPos);

    protected int getStoredLevel(long blockPos) {
        long l = SectionPos.blockToSection(blockPos);
        DataLayer dataLayer = this.getDataLayer(l, true);
        return dataLayer.get(
            SectionPos.sectionRelative(BlockPos.getX(blockPos)),
            SectionPos.sectionRelative(BlockPos.getY(blockPos)),
            SectionPos.sectionRelative(BlockPos.getZ(blockPos))
        );
    }

    protected void setStoredLevel(long blockPos, int value) {
        long l = SectionPos.blockToSection(blockPos);
        DataLayer dataLayer;
        if (this.changedSections.add(l)) {
            dataLayer = this.updatingSectionData.copyDataLayer(l);
        } else {
            dataLayer = this.getDataLayer(l, true);
        }

        dataLayer.set(
            SectionPos.sectionRelative(BlockPos.getX(blockPos)),
            SectionPos.sectionRelative(BlockPos.getY(blockPos)),
            SectionPos.sectionRelative(BlockPos.getZ(blockPos)),
            value
        );
        SectionPos.aroundAndAtBlockPos(blockPos, this.sectionsAffectedByLightUpdates::add);
    }

    protected void markSectionAndNeighborsAsAffected(long id) {
        int i = SectionPos.x(id);
        int j = SectionPos.y(id);
        int k = SectionPos.z(id);

        for (int l = -1; l <= 1; l++) {
            for (int m = -1; m <= 1; m++) {
                for (int n = -1; n <= 1; n++) {
                    this.sectionsAffectedByLightUpdates.add(SectionPos.asLong(i + m, j + n, k + l));
                }
            }
        }
    }

    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dataLayer = this.queuedSections.get(sectionPos);
        return dataLayer != null ? dataLayer : new DataLayer();
    }

    protected boolean hasInconsistencies() {
        return this.hasInconsistencies;
    }

    protected void markNewInconsistencies(LightEngine<M, ?> lightProvider) {
        if (this.hasInconsistencies) {
            this.hasInconsistencies = false;

            for (long l : this.toRemove) {
                DataLayer dataLayer = this.queuedSections.remove(l);
                DataLayer dataLayer2 = this.updatingSectionData.removeLayer(l);
                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(l))) {
                    if (dataLayer != null) {
                        this.queuedSections.put(l, dataLayer);
                    } else if (dataLayer2 != null) {
                        this.queuedSections.put(l, dataLayer2);
                    }
                }
            }

            this.updatingSectionData.clearCache();

            for (long m : this.toRemove) {
                this.onNodeRemoved(m);
                this.changedSections.add(m);
            }

            this.toRemove.clear();
            ObjectIterator<Entry<DataLayer>> objectIterator = Long2ObjectMaps.fastIterator(this.queuedSections);

            while (objectIterator.hasNext()) {
                Entry<DataLayer> entry = objectIterator.next();
                long n = entry.getLongKey();
                if (this.storingLightForSection(n)) {
                    DataLayer dataLayer3 = entry.getValue();
                    if (this.updatingSectionData.getLayer(n) != dataLayer3) {
                        this.updatingSectionData.setLayer(n, dataLayer3);
                        this.changedSections.add(n);
                    }

                    objectIterator.remove();
                }
            }

            this.updatingSectionData.clearCache();
        }
    }

    protected void onNodeAdded(long sectionPos) {
    }

    protected void onNodeRemoved(long sectionPos) {
    }

    protected void setLightEnabled(long columnPos, boolean enabled) {
        if (enabled) {
            this.columnsWithSources.add(columnPos);
        } else {
            this.columnsWithSources.remove(columnPos);
        }
    }

    protected boolean lightOnInSection(long sectionPos) {
        long l = SectionPos.getZeroNode(sectionPos);
        return this.columnsWithSources.contains(l);
    }

    public void retainData(long sectionPos, boolean retain) {
        if (retain) {
            this.columnsToRetainQueuedDataFor.add(sectionPos);
        } else {
            this.columnsToRetainQueuedDataFor.remove(sectionPos);
        }
    }

    protected void queueSectionData(long sectionPos, @Nullable DataLayer array) {
        if (array != null) {
            this.queuedSections.put(sectionPos, array);
            this.hasInconsistencies = true;
        } else {
            this.queuedSections.remove(sectionPos);
        }
    }

    protected void updateSectionStatus(long sectionPos, boolean notReady) {
        byte b = this.sectionStates.get(sectionPos);
        byte c = LayerLightSectionStorage.SectionState.hasData(b, !notReady);
        if (b != c) {
            this.putSectionState(sectionPos, c);
            int i = notReady ? -1 : 1;

            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    for (int l = -1; l <= 1; l++) {
                        if (j != 0 || k != 0 || l != 0) {
                            long m = SectionPos.offset(sectionPos, j, k, l);
                            byte d = this.sectionStates.get(m);
                            this.putSectionState(
                                m, LayerLightSectionStorage.SectionState.neighborCount(d, LayerLightSectionStorage.SectionState.neighborCount(d) + i)
                            );
                        }
                    }
                }
            }
        }
    }

    protected void putSectionState(long sectionPos, byte flags) {
        if (flags != 0) {
            if (this.sectionStates.put(sectionPos, flags) == 0) {
                this.initializeSection(sectionPos);
            }
        } else if (this.sectionStates.remove(sectionPos) != 0) {
            this.removeSection(sectionPos);
        }
    }

    private void initializeSection(long sectionPos) {
        if (!this.toRemove.remove(sectionPos)) {
            this.updatingSectionData.setLayer(sectionPos, this.createDataLayer(sectionPos));
            this.changedSections.add(sectionPos);
            this.onNodeAdded(sectionPos);
            this.markSectionAndNeighborsAsAffected(sectionPos);
            this.hasInconsistencies = true;
        }
    }

    private void removeSection(long sectionPos) {
        this.toRemove.add(sectionPos);
        this.hasInconsistencies = true;
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M dataLayerStorageMap = this.updatingSectionData.copy();
            dataLayerStorageMap.disableCache();
            this.visibleSectionData = dataLayerStorageMap;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            LongIterator longIterator = this.sectionsAffectedByLightUpdates.iterator();

            while (longIterator.hasNext()) {
                long l = longIterator.nextLong();
                this.chunkSource.onLightUpdate(this.layer, SectionPos.of(l));
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(long sectionPos) {
        return LayerLightSectionStorage.SectionState.type(this.sectionStates.get(sectionPos));
    }

    protected static class SectionState {
        public static final byte EMPTY = 0;
        private static final int MIN_NEIGHBORS = 0;
        private static final int MAX_NEIGHBORS = 26;
        private static final byte HAS_DATA_BIT = 32;
        private static final byte NEIGHBOR_COUNT_BITS = 31;

        public static byte hasData(byte packed, boolean ready) {
            return (byte)(ready ? packed | 32 : packed & -33);
        }

        public static byte neighborCount(byte packed, int neighborCount) {
            if (neighborCount >= 0 && neighborCount <= 26) {
                return (byte)(packed & -32 | neighborCount & 31);
            } else {
                throw new IllegalArgumentException("Neighbor count was not within range [0; 26]");
            }
        }

        public static boolean hasData(byte packed) {
            return (packed & 32) != 0;
        }

        public static int neighborCount(byte packed) {
            return packed & 31;
        }

        public static LayerLightSectionStorage.SectionType type(byte packed) {
            if (packed == 0) {
                return LayerLightSectionStorage.SectionType.EMPTY;
            } else {
                return hasData(packed) ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.LIGHT_ONLY;
            }
        }
    }

    public static enum SectionType {
        EMPTY("2"),
        LIGHT_ONLY("1"),
        LIGHT_AND_DATA("0");

        private final String display;

        private SectionType(final String sigil) {
            this.display = sigil;
        }

        public String display() {
            return this.display;
        }
    }
}
