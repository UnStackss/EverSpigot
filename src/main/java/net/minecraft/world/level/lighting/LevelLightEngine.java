package net.minecraft.world.level.lighting;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class LevelLightEngine implements LightEventListener, ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider {
    public static final int LIGHT_SECTION_PADDING = 1;
    protected final LevelHeightAccessor levelHeightAccessor;
    // Paper start - rewrite chunk system
    protected final ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface lightEngine;

    @Override
    public final ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface starlight$getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public void starlight$clientUpdateLight(final LightLayer lightType, final SectionPos pos,
                                            final DataLayer nibble, final boolean trustEdges) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY"); // Paper - not implemented on server
    }

    @Override
    public void starlight$clientRemoveLightData(final ChunkPos chunkPos) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY"); // Paper - not implemented on server
    }

    @Override
    public void starlight$clientChunkLoad(final ChunkPos pos, final net.minecraft.world.level.chunk.LevelChunk chunk) {
        throw new IllegalStateException("This hook is for the CLIENT ONLY"); // Paper - not implemented on server
    }
    // Paper end - rewrite chunk system

    public LevelLightEngine(LightChunkGetter chunkProvider, boolean hasBlockLight, boolean hasSkyLight) {
        this.levelHeightAccessor = chunkProvider.getLevel();
        // Paper start - rewrite chunk system
        if (chunkProvider.getLevel() instanceof net.minecraft.world.level.Level) {
            this.lightEngine = new ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface(chunkProvider, hasSkyLight, hasBlockLight, (LevelLightEngine)(Object)this);
        } else {
            this.lightEngine = new ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface(null, hasSkyLight, hasBlockLight, (LevelLightEngine)(Object)this);
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public void checkBlock(BlockPos pos) {
        this.lightEngine.blockChange(pos.immutable()); // Paper - rewrite chunk system
    }

    @Override
    public boolean hasLightWork() {
        return this.lightEngine.hasUpdates(); // Paper - rewrite chunk system
    }

    @Override
    public int runLightUpdates() {
        final boolean hadUpdates = this.hasLightWork();
        this.lightEngine.propagateChanges();
        return hadUpdates ? 1 : 0; // Paper - rewrite chunk system
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean notReady) {
        this.lightEngine.sectionChange(pos, notReady); // Paper - rewrite chunk system
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean retainData) {
        // Paper - rewrite chunk system
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        // Paper - rewrite chunk system
    }

    public LayerLightEventListener getLayerListener(LightLayer lightType) {
        return lightType == LightLayer.BLOCK ? this.lightEngine.getBlockReader() : this.lightEngine.getSkyReader(); // Paper - rewrite chunk system
    }

    public String getDebugData(LightLayer lightType, SectionPos pos) {
        return "n/a"; // Paper - rewrite chunk system
    }

    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer lightType, SectionPos pos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system
    }

    public void queueSectionData(LightLayer lightType, SectionPos pos, @Nullable DataLayer nibbles) {
        // Paper - rewrite chunk system
    }

    public void retainData(ChunkPos pos, boolean retainData) {
        // Paper - rewrite chunk system
    }

    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        return this.lightEngine.getRawBrightness(pos, ambientDarkness); // Paper - rewrite chunk system
    }

    public boolean lightOnInSection(SectionPos sectionPos) {
        throw new UnsupportedOperationException(); // Paper - rewrite chunk system // Paper - not implemented on server
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
