package net.minecraft.world.level.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class EmptyLevelChunk extends LevelChunk implements ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk { // Paper - rewrite chunk system
    private final Holder<Biome> biome;

    public EmptyLevelChunk(Level world, ChunkPos pos, Holder<Biome> biomeEntry) {
        super(world, pos);
        this.biome = biomeEntry;
    }

    // Paper start - rewrite chunk system
    @Override
    public ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] starlight$getBlockNibbles() {
        return ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine.getFilledEmptyLight(this.getLevel());
    }

    @Override
    public void starlight$setBlockNibbles(final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] nibbles) {}

    @Override
    public ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] starlight$getSkyNibbles() {
        return ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine.getFilledEmptyLight(this.getLevel());
    }

    @Override
    public void starlight$setSkyNibbles(final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] nibbles) {}

    @Override
    public boolean[] starlight$getSkyEmptinessMap() {
        return null;
    }

    @Override
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap) {}

    @Override
    public boolean[] starlight$getBlockEmptinessMap() {
        return null;
    }

    @Override
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap) {}
    // Paper end - rewrite chunk system

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.VOID_AIR.defaultBlockState();
    }
    // Paper start
    @Override
    public BlockState getBlockState(final int x, final int y, final int z) {
        return Blocks.VOID_AIR.defaultBlockState();
    }
    // Paper end

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        return null;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getLightEmission(BlockPos pos) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationType) {
        return null;
    }

    @Override
    public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean isYSpaceEmpty(int lowerHeight, int upperHeight) {
        return true;
    }

    @Override
    public boolean isSectionEmpty(int sectionCoord) {
        return true;
    }

    @Override
    public FullChunkStatus getFullStatus() {
        return FullChunkStatus.FULL;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        return this.biome;
    }
}
