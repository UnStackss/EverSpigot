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

public class EmptyLevelChunk extends LevelChunk {
    private final Holder<Biome> biome;

    public EmptyLevelChunk(Level world, ChunkPos pos, Holder<Biome> biomeEntry) {
        super(world, pos);
        this.biome = biomeEntry;
    }

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
