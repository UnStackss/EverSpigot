package net.minecraft.world.level.lighting;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class LevelLightEngine implements LightEventListener {
    public static final int LIGHT_SECTION_PADDING = 1;
    protected final LevelHeightAccessor levelHeightAccessor;
    @Nullable
    private final LightEngine<?, ?> blockEngine;
    @Nullable
    private final LightEngine<?, ?> skyEngine;

    public LevelLightEngine(LightChunkGetter chunkProvider, boolean hasBlockLight, boolean hasSkyLight) {
        this.levelHeightAccessor = chunkProvider.getLevel();
        this.blockEngine = hasBlockLight ? new BlockLightEngine(chunkProvider) : null;
        this.skyEngine = hasSkyLight ? new SkyLightEngine(chunkProvider) : null;
    }

    @Override
    public void checkBlock(BlockPos pos) {
        if (this.blockEngine != null) {
            this.blockEngine.checkBlock(pos);
        }

        if (this.skyEngine != null) {
            this.skyEngine.checkBlock(pos);
        }
    }

    @Override
    public boolean hasLightWork() {
        return this.skyEngine != null && this.skyEngine.hasLightWork() || this.blockEngine != null && this.blockEngine.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        int i = 0;
        if (this.blockEngine != null) {
            i += this.blockEngine.runLightUpdates();
        }

        if (this.skyEngine != null) {
            i += this.skyEngine.runLightUpdates();
        }

        return i;
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean notReady) {
        if (this.blockEngine != null) {
            this.blockEngine.updateSectionStatus(pos, notReady);
        }

        if (this.skyEngine != null) {
            this.skyEngine.updateSectionStatus(pos, notReady);
        }
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean retainData) {
        if (this.blockEngine != null) {
            this.blockEngine.setLightEnabled(pos, retainData);
        }

        if (this.skyEngine != null) {
            this.skyEngine.setLightEnabled(pos, retainData);
        }
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        if (this.blockEngine != null) {
            this.blockEngine.propagateLightSources(chunkPos);
        }

        if (this.skyEngine != null) {
            this.skyEngine.propagateLightSources(chunkPos);
        }
    }

    public LayerLightEventListener getLayerListener(LightLayer lightType) {
        if (lightType == LightLayer.BLOCK) {
            return (LayerLightEventListener)(this.blockEngine == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.blockEngine);
        } else {
            return (LayerLightEventListener)(this.skyEngine == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : this.skyEngine);
        }
    }

    public String getDebugData(LightLayer lightType, SectionPos pos) {
        if (lightType == LightLayer.BLOCK) {
            if (this.blockEngine != null) {
                return this.blockEngine.getDebugData(pos.asLong());
            }
        } else if (this.skyEngine != null) {
            return this.skyEngine.getDebugData(pos.asLong());
        }

        return "n/a";
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer lightType, SectionPos pos) {
        if (lightType == LightLayer.BLOCK) {
            if (this.blockEngine != null) {
                return this.blockEngine.getDebugSectionType(pos.asLong());
            }
        } else if (this.skyEngine != null) {
            return this.skyEngine.getDebugSectionType(pos.asLong());
        }

        return LayerLightSectionStorage.SectionType.EMPTY;
    }

    public void queueSectionData(LightLayer lightType, SectionPos pos, @Nullable DataLayer nibbles) {
        if (lightType == LightLayer.BLOCK) {
            if (this.blockEngine != null) {
                this.blockEngine.queueSectionData(pos.asLong(), nibbles);
            }
        } else if (this.skyEngine != null) {
            this.skyEngine.queueSectionData(pos.asLong(), nibbles);
        }
    }

    public void retainData(ChunkPos pos, boolean retainData) {
        if (this.blockEngine != null) {
            this.blockEngine.retainData(pos, retainData);
        }

        if (this.skyEngine != null) {
            this.skyEngine.retainData(pos, retainData);
        }
    }

    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        int i = this.skyEngine == null ? 0 : this.skyEngine.getLightValue(pos) - ambientDarkness;
        int j = this.blockEngine == null ? 0 : this.blockEngine.getLightValue(pos);
        return Math.max(j, i);
    }

    public boolean lightOnInSection(SectionPos sectionPos) {
        long l = sectionPos.asLong();
        return this.blockEngine == null
            || this.blockEngine.storage.lightOnInSection(l) && (this.skyEngine == null || this.skyEngine.storage.lightOnInSection(l));
    }

    public int getLightSectionCount() {
        return this.levelHeightAccessor.getSectionsCount() + 2;
    }

    public int getMinLightSection() {
        return this.levelHeightAccessor.getMinSection() - 1;
    }

    public int getMaxLightSection() {
        return this.getMinLightSection() + this.getLightSectionCount();
    }
}
