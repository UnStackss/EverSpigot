package net.minecraft.world.level.chunk;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class LevelChunkSection implements ca.spottedleaf.moonrise.patches.block_counting.BlockCountingChunkSection { // Paper - block counting

    public static final int SECTION_WIDTH = 16;
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTION_SIZE = 4096;
    public static final int BIOME_CONTAINER_BITS = 2;
    short nonEmptyBlockCount; // Paper - package private
    private short tickingBlockCount;
    private short tickingFluidCount;
    public final PalettedContainer<BlockState> states;
    // CraftBukkit start - read/write
    private PalettedContainer<Holder<Biome>> biomes;

    // Paper start - block counting
    private static final it.unimi.dsi.fastutil.ints.IntArrayList FULL_LIST = new it.unimi.dsi.fastutil.ints.IntArrayList(16*16*16);
    static {
        for (int i = 0; i < (16*16*16); ++i) {
            FULL_LIST.add(i);
        }
    }

    private int specialCollidingBlocks;
    private final ca.spottedleaf.moonrise.common.list.IBlockDataList tickingBlocks = new ca.spottedleaf.moonrise.common.list.IBlockDataList();

    @Override
    public final int moonrise$getSpecialCollidingBlocks() {
        return this.specialCollidingBlocks;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.IBlockDataList moonrise$getTickingBlockList() {
        return this.tickingBlocks;
    }
    // Paper end - block counting

    public LevelChunkSection(PalettedContainer<BlockState> datapaletteblock, PalettedContainer<Holder<Biome>> palettedcontainerro) {
        // CraftBukkit end
        this.states = datapaletteblock;
        this.biomes = palettedcontainerro;
        this.recalcBlockCounts();
    }

    // Paper start - Anti-Xray - Add parameters
    @Deprecated @io.papermc.paper.annotation.DoNotUse public LevelChunkSection(Registry<Biome> biomeRegistry) { this(biomeRegistry, null, null, 0); }
    public LevelChunkSection(Registry<Biome> biomeRegistry, net.minecraft.world.level.Level level, net.minecraft.world.level.ChunkPos chunkPos, int chunkSectionY) {
        // Paper end
        this.states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES, level == null || level.chunkPacketBlockController == null ? null : level.chunkPacketBlockController.getPresetBlockStates(level, chunkPos, chunkSectionY)); // Paper - Anti-Xray - Add preset block states
        this.biomes = new PalettedContainer<>(biomeRegistry.asHolderIdMap(), biomeRegistry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES, null); // Paper - Anti-Xray - Add preset biomes
    }

    public BlockState getBlockState(int x, int y, int z) {
        return (BlockState) this.states.get(x, y, z);
    }

    public FluidState getFluidState(int x, int y, int z) {
        return this.states.get(x, y, z).getFluidState(); // Paper - Perf: Optimise Chunk#getFluid; diff on change - we expect this to be effectively just getType(x, y, z).getFluid(). If this changes we need to check other patches that use IBlockData#getFluid.
    }

    public void acquire() {
        this.states.acquire();
    }

    public void release() {
        this.states.release();
    }

    public BlockState setBlockState(int x, int y, int z, BlockState state) {
        return this.setBlockState(x, y, z, state, true);
    }

    public BlockState setBlockState(int x, int y, int z, BlockState state, boolean lock) {
        BlockState iblockdata1;

        if (lock) {
            iblockdata1 = (BlockState) this.states.getAndSet(x, y, z, state);
        } else {
            iblockdata1 = (BlockState) this.states.getAndSetUnchecked(x, y, z, state);
        }

        FluidState fluid = iblockdata1.getFluidState();
        FluidState fluid1 = state.getFluidState();

        if (!iblockdata1.isAir()) {
            --this.nonEmptyBlockCount;
            if (iblockdata1.isRandomlyTicking()) {
                --this.tickingBlockCount;
            }
        }

        if (!fluid.isEmpty()) {
            --this.tickingFluidCount;
        }

        if (!state.isAir()) {
            ++this.nonEmptyBlockCount;
            if (state.isRandomlyTicking()) {
                ++this.tickingBlockCount;
            }
        }

        if (!fluid1.isEmpty()) {
            ++this.tickingFluidCount;
        }

        // Paper start - block counting
        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(iblockdata1)) {
            --this.specialCollidingBlocks;
        }
        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(state)) {
            ++this.specialCollidingBlocks;
        }

        if (iblockdata1.isRandomlyTicking()) {
            this.tickingBlocks.remove(x, y, z);
        }
        if (state.isRandomlyTicking()) {
            this.tickingBlocks.add(x, y, z, state);
        }
        // Paper end - block counting

        return iblockdata1;
    }

    public boolean hasOnlyAir() {
        return this.nonEmptyBlockCount == 0;
    }

    public boolean isRandomlyTicking() {
        return this.isRandomlyTickingBlocks() || this.isRandomlyTickingFluids();
    }

    public boolean isRandomlyTickingBlocks() {
        return this.tickingBlockCount > 0;
    }

    public boolean isRandomlyTickingFluids() {
        return this.tickingFluidCount > 0;
    }

    public void recalcBlockCounts() {
        // Paper start - block counting
        // reset, then recalculate
        this.nonEmptyBlockCount = (short)0;
        this.tickingBlockCount = (short)0;
        this.tickingFluidCount = (short)0;
        this.specialCollidingBlocks = (short)0;
        this.tickingBlocks.clear();

        if (this.maybeHas((final BlockState state) -> !state.isAir())) {
            final PalettedContainer.Data<BlockState> data = this.states.data;
            final Palette<BlockState> palette = data.palette();
            final int paletteSize = palette.getSize();
            final net.minecraft.util.BitStorage storage = data.storage();

            final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntArrayList> counts;
            if (paletteSize == 1) {
                counts = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(1);
                counts.put(0, FULL_LIST);
            } else {
                counts = ((ca.spottedleaf.moonrise.patches.block_counting.BlockCountingBitStorage)storage).moonrise$countEntries();
            }

            for (final java.util.Iterator<it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<it.unimi.dsi.fastutil.ints.IntArrayList>> iterator = counts.int2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                final it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<it.unimi.dsi.fastutil.ints.IntArrayList> entry = iterator.next();
                final int paletteIdx = entry.getIntKey();
                final it.unimi.dsi.fastutil.ints.IntArrayList coordinates = entry.getValue();
                final int paletteCount = coordinates.size();

                final BlockState state = palette.valueFor(paletteIdx);

                if (state.isAir()) {
                    continue;
                }

                if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isSpecialCollidingBlock(state)) {
                    this.specialCollidingBlocks += paletteCount;
                }
                this.nonEmptyBlockCount += paletteCount;
                if (state.isRandomlyTicking()) {
                    this.tickingBlockCount += paletteCount;
                    final int[] raw = coordinates.elements();

                    java.util.Objects.checkFromToIndex(0, paletteCount, raw.length);
                    for (int i = 0; i < paletteCount; ++i) {
                        this.tickingBlocks.add(raw[i], state);
                    }
                }

                final FluidState fluid = state.getFluidState();

                if (!fluid.isEmpty()) {
                    //this.nonEmptyBlockCount += count; // fix vanilla bug: make non empty block count correct
                    if (fluid.isRandomlyTicking()) {
                        this.tickingFluidCount += paletteCount;
                    }
                }
            }
        }
        // Paper end - block counting
    }

    public PalettedContainer<BlockState> getStates() {
        return this.states;
    }

    public PalettedContainerRO<Holder<Biome>> getBiomes() {
        return this.biomes;
    }

    public void read(FriendlyByteBuf buf) {
        this.nonEmptyBlockCount = buf.readShort();
        this.states.read(buf);
        PalettedContainer<Holder<Biome>> datapaletteblock = this.biomes.recreate();

        datapaletteblock.read(buf);
        this.biomes = datapaletteblock;
        this.recalcBlockCounts(); // Paper - block counting
    }

    public void readBiomes(FriendlyByteBuf buf) {
        PalettedContainer<Holder<Biome>> datapaletteblock = this.biomes.recreate();

        datapaletteblock.read(buf);
        this.biomes = datapaletteblock;
    }

    // Paper start - Anti-Xray - Add chunk packet info
    @Deprecated @io.papermc.paper.annotation.DoNotUse public void write(FriendlyByteBuf buf) { this.write(buf, null, 0); }
    public void write(FriendlyByteBuf buf, com.destroystokyo.paper.antixray.ChunkPacketInfo<BlockState> chunkPacketInfo, int chunkSectionIndex) {
        buf.writeShort(this.nonEmptyBlockCount);
        this.states.write(buf, chunkPacketInfo, chunkSectionIndex);
        this.biomes.write(buf, null, chunkSectionIndex);
        // Paper end
    }

    public int getSerializedSize() {
        return 2 + this.states.getSerializedSize() + this.biomes.getSerializedSize();
    }

    public boolean maybeHas(Predicate<BlockState> predicate) {
        return this.states.maybeHas(predicate);
    }

    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return (Holder) this.biomes.get(x, y, z);
    }

    // CraftBukkit start
    public void setBiome(int i, int j, int k, Holder<Biome> biome) {
        this.biomes.set(i, j, k, biome);
    }
    // CraftBukkit end

    public void fillBiomesFromNoise(BiomeResolver biomeSupplier, Climate.Sampler sampler, int x, int y, int z) {
        PalettedContainer<Holder<Biome>> datapaletteblock = this.biomes.recreate();
        boolean flag = true;

        for (int l = 0; l < 4; ++l) {
            for (int i1 = 0; i1 < 4; ++i1) {
                for (int j1 = 0; j1 < 4; ++j1) {
                    datapaletteblock.getAndSetUnchecked(l, i1, j1, biomeSupplier.getNoiseBiome(x + l, y + i1, z + j1, sampler));
                }
            }
        }

        this.biomes = datapaletteblock;
    }
}
