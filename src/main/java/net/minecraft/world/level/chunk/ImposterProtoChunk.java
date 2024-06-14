package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.TickContainerAccess;

public class ImposterProtoChunk extends ProtoChunk implements ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk { // Paper - rewrite chunk system
    private final LevelChunk wrapped;
    private final boolean allowWrites;

    public ImposterProtoChunk(LevelChunk wrapped, boolean propagateToWrapped) {
        super(
            wrapped.getPos(),
            UpgradeData.EMPTY,
            wrapped.levelHeightAccessor,
            wrapped.getLevel().registryAccess().registryOrThrow(Registries.BIOME),
            wrapped.getBlendingData()
        );
        this.wrapped = wrapped;
        this.allowWrites = propagateToWrapped;
    }

    // Paper start - rewrite chunk system
    @Override
    public ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] starlight$getBlockNibbles() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getBlockNibbles();
    }

    @Override
    public void starlight$setBlockNibbles(final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] nibbles) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setBlockNibbles(nibbles);
    }

    @Override
    public ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] starlight$getSkyNibbles() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getSkyNibbles();
    }

    @Override
    public void starlight$setSkyNibbles(final ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray[] nibbles) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setSkyNibbles(nibbles);
    }

    @Override
    public boolean[] starlight$getSkyEmptinessMap() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getSkyEmptinessMap();
    }

    @Override
    public void starlight$setSkyEmptinessMap(final boolean[] emptinessMap) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setSkyEmptinessMap(emptinessMap);
    }

    @Override
    public boolean[] starlight$getBlockEmptinessMap() {
        return ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$getBlockEmptinessMap();
    }

    @Override
    public void starlight$setBlockEmptinessMap(final boolean[] emptinessMap) {
        ((ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk)this.wrapped).starlight$setBlockEmptinessMap(emptinessMap);
    }
    // Paper end - rewrite chunk system

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.wrapped.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.wrapped.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.wrapped.getFluidState(pos);
    }

    @Override
    public int getMaxLightLevel() {
        return this.wrapped.getMaxLightLevel();
    }

    @Override
    public LevelChunkSection getSection(int yIndex) {
        return this.allowWrites ? this.wrapped.getSection(yIndex) : super.getSection(yIndex);
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moved) {
        return this.allowWrites ? this.wrapped.setBlockState(pos, state, moved) : null;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        if (this.allowWrites) {
            this.wrapped.setBlockEntity(blockEntity);
        }
    }

    @Override
    public void addEntity(Entity entity) {
        if (this.allowWrites) {
            this.wrapped.addEntity(entity);
        }
    }

    @Override
    public void setPersistedStatus(ChunkStatus status) {
        if (this.allowWrites) {
            super.setPersistedStatus(status);
        }
    }

    @Override
    public LevelChunkSection[] getSections() {
        return this.wrapped.getSections();
    }

    @Override
    public void setHeightmap(Heightmap.Types type, long[] heightmap) {
    }

    private Heightmap.Types fixType(Heightmap.Types type) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG) {
            return Heightmap.Types.WORLD_SURFACE;
        } else {
            return type == Heightmap.Types.OCEAN_FLOOR_WG ? Heightmap.Types.OCEAN_FLOOR : type;
        }
    }

    @Override
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return this.wrapped.getOrCreateHeightmapUnprimed(type);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return this.wrapped.getHeight(this.fixType(type), x, z);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        return this.wrapped.getNoiseBiome(biomeX, biomeY, biomeZ);
    }

    @Override
    public ChunkPos getPos() {
        return this.wrapped.getPos();
    }

    @Nullable
    @Override
    public StructureStart getStartForStructure(Structure structure) {
        return this.wrapped.getStartForStructure(structure);
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart start) {
    }

    @Override
    public Map<Structure, StructureStart> getAllStarts() {
        return this.wrapped.getAllStarts();
    }

    @Override
    public void setAllStarts(Map<Structure, StructureStart> structureStarts) {
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return this.wrapped.getReferencesForStructure(structure);
    }

    @Override
    public void addReferenceForStructure(Structure structure, long reference) {
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        return this.wrapped.getAllReferences();
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> structureReferences) {
    }

    @Override
    public void setUnsaved(boolean needsSaving) {
        this.wrapped.setUnsaved(needsSaving);
    }

    @Override
    public boolean isUnsaved() {
        return false;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return this.wrapped.getPersistedStatus();
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
    }

    @Override
    public void setBlockEntityNbt(CompoundTag nbt) {
    }

    @Nullable
    @Override
    public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.wrapped.getBlockEntityNbt(pos);
    }

    @Nullable
    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registryLookup) {
        return this.wrapped.getBlockEntityNbtForSaving(pos, registryLookup);
    }

    @Override
    public void findBlocks(Predicate<BlockState> predicate, BiConsumer<BlockPos, BlockState> consumer) {
        this.wrapped.findBlocks(predicate, consumer);
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.allowWrites ? this.wrapped.getBlockTicks() : BlackholeTickAccess.emptyContainer();
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.allowWrites ? this.wrapped.getFluidTicks() : BlackholeTickAccess.emptyContainer();
    }

    @Override
    public ChunkAccess.TicksToSave getTicksForSerialization() {
        return this.wrapped.getTicksForSerialization();
    }

    @Nullable
    @Override
    public BlendingData getBlendingData() {
        return this.wrapped.getBlendingData();
    }

    @Override
    public void setBlendingData(BlendingData blendingData) {
        this.wrapped.setBlendingData(blendingData);
    }

    @Override
    public CarvingMask getCarvingMask(GenerationStep.Carving step) {
        if (this.allowWrites) {
            return super.getCarvingMask(step);
        } else {
            throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
        }
    }

    @Override
    public CarvingMask getOrCreateCarvingMask(GenerationStep.Carving step) {
        if (this.allowWrites) {
            return super.getOrCreateCarvingMask(step);
        } else {
            throw (UnsupportedOperationException)Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
        }
    }

    public LevelChunk getWrapped() {
        return this.wrapped;
    }

    @Override
    public boolean isLightCorrect() {
        return this.wrapped.isLightCorrect();
    }

    @Override
    public void setLightCorrect(boolean lightOn) {
        this.wrapped.setLightCorrect(lightOn);
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver biomeSupplier, Climate.Sampler sampler) {
        if (this.allowWrites) {
            this.wrapped.fillBiomesFromNoise(biomeSupplier, sampler);
        }
    }

    @Override
    public void initializeLightSources() {
        this.wrapped.initializeLightSources();
    }

    @Override
    public ChunkSkyLightSources getSkyLightSources() {
        return this.wrapped.getSkyLightSources();
    }
}
