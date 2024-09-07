package net.minecraft.world.level.levelgen.blending;

import com.google.common.primitives.Doubles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public class BlendingData {
    private static final double BLENDING_DENSITY_FACTOR = 0.1;
    protected static final int CELL_WIDTH = 4;
    protected static final int CELL_HEIGHT = 8;
    protected static final int CELL_RATIO = 2;
    private static final double SOLID_DENSITY = 1.0;
    private static final double AIR_DENSITY = -1.0;
    private static final int CELLS_PER_SECTION_Y = 2;
    private static final int QUARTS_PER_SECTION = QuartPos.fromBlock(16);
    private static final int CELL_HORIZONTAL_MAX_INDEX_INSIDE = QUARTS_PER_SECTION - 1;
    private static final int CELL_HORIZONTAL_MAX_INDEX_OUTSIDE = QUARTS_PER_SECTION;
    private static final int CELL_COLUMN_INSIDE_COUNT = 2 * CELL_HORIZONTAL_MAX_INDEX_INSIDE + 1;
    private static final int CELL_COLUMN_OUTSIDE_COUNT = 2 * CELL_HORIZONTAL_MAX_INDEX_OUTSIDE + 1;
    private static final int CELL_COLUMN_COUNT = CELL_COLUMN_INSIDE_COUNT + CELL_COLUMN_OUTSIDE_COUNT;
    private final LevelHeightAccessor areaWithOldGeneration;
    private static final List<Block> SURFACE_BLOCKS = List.of(
        Blocks.PODZOL,
        Blocks.GRAVEL,
        Blocks.GRASS_BLOCK,
        Blocks.STONE,
        Blocks.COARSE_DIRT,
        Blocks.SAND,
        Blocks.RED_SAND,
        Blocks.MYCELIUM,
        Blocks.SNOW_BLOCK,
        Blocks.TERRACOTTA,
        Blocks.DIRT
    );
    protected static final double NO_VALUE = Double.MAX_VALUE;
    private boolean hasCalculatedData;
    private final double[] heights;
    private final List<List<Holder<Biome>>> biomes;
    private final transient double[][] densities;
    private static final Codec<double[]> DOUBLE_ARRAY_CODEC = Codec.DOUBLE.listOf().xmap(Doubles::toArray, Doubles::asList);
    public static final Codec<BlendingData> CODEC = RecordCodecBuilder.<BlendingData>create(
            instance -> instance.group(
                        Codec.INT.fieldOf("min_section").forGetter(blendingData -> blendingData.areaWithOldGeneration.getMinSection()),
                        Codec.INT.fieldOf("max_section").forGetter(blendingData -> blendingData.areaWithOldGeneration.getMaxSection()),
                        DOUBLE_ARRAY_CODEC.lenientOptionalFieldOf("heights")
                            .forGetter(
                                blendingData -> DoubleStream.of(blendingData.heights).anyMatch(height -> height != Double.MAX_VALUE)
                                        ? Optional.of(blendingData.heights)
                                        : Optional.empty()
                            )
                    )
                    .apply(instance, BlendingData::new)
        )
        .comapFlatMap(BlendingData::validateArraySize, Function.identity());

    private static DataResult<BlendingData> validateArraySize(BlendingData data) {
        return data.heights.length != CELL_COLUMN_COUNT ? DataResult.error(() -> "heights has to be of length " + CELL_COLUMN_COUNT) : DataResult.success(data);
    }

    private BlendingData(int oldBottomSectionY, int oldTopSectionY, Optional<double[]> heights) {
        this.heights = heights.orElse(Util.make(new double[CELL_COLUMN_COUNT], heights2 -> Arrays.fill(heights2, Double.MAX_VALUE)));
        this.densities = new double[CELL_COLUMN_COUNT][];
        ObjectArrayList<List<Holder<Biome>>> objectArrayList = new ObjectArrayList<>(CELL_COLUMN_COUNT);
        objectArrayList.size(CELL_COLUMN_COUNT);
        this.biomes = objectArrayList;
        int i = SectionPos.sectionToBlockCoord(oldBottomSectionY);
        int j = SectionPos.sectionToBlockCoord(oldTopSectionY) - i;
        this.areaWithOldGeneration = LevelHeightAccessor.create(i, j);
    }

    @Nullable
    public static BlendingData getOrUpdateBlendingData(WorldGenRegion chunkRegion, int chunkX, int chunkZ) {
        ChunkAccess chunkAccess = chunkRegion.getChunk(chunkX, chunkZ);
        BlendingData blendingData = chunkAccess.getBlendingData();
        if (blendingData != null && !chunkAccess.getHighestGeneratedStatus().isBefore(ChunkStatus.BIOMES)) {
            blendingData.calculateData(chunkAccess, sideByGenerationAge(chunkRegion, chunkX, chunkZ, false));
            return blendingData;
        } else {
            return null;
        }
    }

    public static Set<Direction8> sideByGenerationAge(WorldGenLevel access, int chunkX, int chunkZ, boolean oldNoise) {
        Set<Direction8> set = EnumSet.noneOf(Direction8.class);

        for (Direction8 direction8 : Direction8.values()) {
            int i = chunkX + direction8.getStepX();
            int j = chunkZ + direction8.getStepZ();
            if (access.getChunk(i, j).isOldNoiseGeneration() == oldNoise) {
                set.add(direction8);
            }
        }

        return set;
    }

    private void calculateData(ChunkAccess chunk, Set<Direction8> newNoiseChunkDirections) {
        if (!this.hasCalculatedData) {
            if (newNoiseChunkDirections.contains(Direction8.NORTH)
                || newNoiseChunkDirections.contains(Direction8.WEST)
                || newNoiseChunkDirections.contains(Direction8.NORTH_WEST)) {
                this.addValuesForColumn(getInsideIndex(0, 0), chunk, 0, 0);
            }

            if (newNoiseChunkDirections.contains(Direction8.NORTH)) {
                for (int i = 1; i < QUARTS_PER_SECTION; i++) {
                    this.addValuesForColumn(getInsideIndex(i, 0), chunk, 4 * i, 0);
                }
            }

            if (newNoiseChunkDirections.contains(Direction8.WEST)) {
                for (int j = 1; j < QUARTS_PER_SECTION; j++) {
                    this.addValuesForColumn(getInsideIndex(0, j), chunk, 0, 4 * j);
                }
            }

            if (newNoiseChunkDirections.contains(Direction8.EAST)) {
                for (int k = 1; k < QUARTS_PER_SECTION; k++) {
                    this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, k), chunk, 15, 4 * k);
                }
            }

            if (newNoiseChunkDirections.contains(Direction8.SOUTH)) {
                for (int l = 0; l < QUARTS_PER_SECTION; l++) {
                    this.addValuesForColumn(getOutsideIndex(l, CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 4 * l, 15);
                }
            }

            if (newNoiseChunkDirections.contains(Direction8.EAST) && newNoiseChunkDirections.contains(Direction8.NORTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, 0), chunk, 15, 0);
            }

            if (newNoiseChunkDirections.contains(Direction8.EAST)
                && newNoiseChunkDirections.contains(Direction8.SOUTH)
                && newNoiseChunkDirections.contains(Direction8.SOUTH_EAST)) {
                this.addValuesForColumn(getOutsideIndex(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE, CELL_HORIZONTAL_MAX_INDEX_OUTSIDE), chunk, 15, 15);
            }

            this.hasCalculatedData = true;
        }
    }

    private void addValuesForColumn(int index, ChunkAccess chunk, int chunkBlockX, int chunkBlockZ) {
        if (this.heights[index] == Double.MAX_VALUE) {
            this.heights[index] = (double)this.getHeightAtXZ(chunk, chunkBlockX, chunkBlockZ);
        }

        this.densities[index] = this.getDensityColumn(chunk, chunkBlockX, chunkBlockZ, Mth.floor(this.heights[index]));
        this.biomes.set(index, this.getBiomeColumn(chunk, chunkBlockX, chunkBlockZ));
    }

    private int getHeightAtXZ(ChunkAccess chunk, int blockX, int blockZ) {
        int i;
        if (chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG)) {
            i = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, blockX, blockZ) + 1, this.areaWithOldGeneration.getMaxBuildHeight());
        } else {
            i = this.areaWithOldGeneration.getMaxBuildHeight();
        }

        int k = this.areaWithOldGeneration.getMinBuildHeight();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(blockX, i, blockZ);

        while (mutableBlockPos.getY() > k) {
            mutableBlockPos.move(Direction.DOWN);
            if (SURFACE_BLOCKS.contains(chunk.getBlockState(mutableBlockPos).getBlock())) {
                return mutableBlockPos.getY();
            }
        }

        return k;
    }

    private static double read1(ChunkAccess chunk, BlockPos.MutableBlockPos mutablePos) {
        return isGround(chunk, mutablePos.move(Direction.DOWN)) ? 1.0 : -1.0;
    }

    private static double read7(ChunkAccess chunk, BlockPos.MutableBlockPos mutablePos) {
        double d = 0.0;

        for (int i = 0; i < 7; i++) {
            d += read1(chunk, mutablePos);
        }

        return d;
    }

    private double[] getDensityColumn(ChunkAccess chunk, int chunkBlockX, int chunkBlockZ, int surfaceHeight) {
        double[] ds = new double[this.cellCountPerColumn()];
        Arrays.fill(ds, -1.0);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(chunkBlockX, this.areaWithOldGeneration.getMaxBuildHeight(), chunkBlockZ);
        double d = read7(chunk, mutableBlockPos);

        for (int i = ds.length - 2; i >= 0; i--) {
            double e = read1(chunk, mutableBlockPos);
            double f = read7(chunk, mutableBlockPos);
            ds[i] = (d + e + f) / 15.0;
            d = f;
        }

        int j = this.getCellYIndex(Mth.floorDiv(surfaceHeight, 8));
        if (j >= 0 && j < ds.length - 1) {
            double g = ((double)surfaceHeight + 0.5) % 8.0 / 8.0;
            double h = (1.0 - g) / g;
            double k = Math.max(h, 1.0) * 0.25;
            ds[j + 1] = -h / k;
            ds[j] = 1.0 / k;
        }

        return ds;
    }

    private List<Holder<Biome>> getBiomeColumn(ChunkAccess chunk, int chunkBlockX, int chunkBlockZ) {
        ObjectArrayList<Holder<Biome>> objectArrayList = new ObjectArrayList<>(this.quartCountPerColumn());
        objectArrayList.size(this.quartCountPerColumn());

        for (int i = 0; i < objectArrayList.size(); i++) {
            int j = i + QuartPos.fromBlock(this.areaWithOldGeneration.getMinBuildHeight());
            objectArrayList.set(i, chunk.getNoiseBiome(QuartPos.fromBlock(chunkBlockX), j, QuartPos.fromBlock(chunkBlockZ)));
        }

        return objectArrayList;
    }

    private static boolean isGround(ChunkAccess chunk, BlockPos pos) {
        BlockState blockState = chunk.getBlockState(pos);
        return !blockState.isAir()
            && !blockState.is(BlockTags.LEAVES)
            && !blockState.is(BlockTags.LOGS)
            && !blockState.is(Blocks.BROWN_MUSHROOM_BLOCK)
            && !blockState.is(Blocks.RED_MUSHROOM_BLOCK)
            && !blockState.getCollisionShape(chunk, pos).isEmpty();
    }

    protected double getHeight(int biomeX, int biomeY, int biomeZ) {
        if (biomeX == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE || biomeZ == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE) {
            return this.heights[getOutsideIndex(biomeX, biomeZ)];
        } else {
            return biomeX != 0 && biomeZ != 0 ? Double.MAX_VALUE : this.heights[getInsideIndex(biomeX, biomeZ)];
        }
    }

    private double getDensity(@Nullable double[] collidableBlockDensityColumn, int halfSectionY) {
        if (collidableBlockDensityColumn == null) {
            return Double.MAX_VALUE;
        } else {
            int i = this.getCellYIndex(halfSectionY);
            return i >= 0 && i < collidableBlockDensityColumn.length ? collidableBlockDensityColumn[i] * 0.1 : Double.MAX_VALUE;
        }
    }

    protected double getDensity(int chunkBiomeX, int halfSectionY, int chunkBiomeZ) {
        if (halfSectionY == this.getMinY()) {
            return 0.1;
        } else if (chunkBiomeX == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE || chunkBiomeZ == CELL_HORIZONTAL_MAX_INDEX_OUTSIDE) {
            return this.getDensity(this.densities[getOutsideIndex(chunkBiomeX, chunkBiomeZ)], halfSectionY);
        } else {
            return chunkBiomeX != 0 && chunkBiomeZ != 0
                ? Double.MAX_VALUE
                : this.getDensity(this.densities[getInsideIndex(chunkBiomeX, chunkBiomeZ)], halfSectionY);
        }
    }

    protected void iterateBiomes(int biomeX, int biomeY, int biomeZ, BlendingData.BiomeConsumer consumer) {
        if (biomeY >= QuartPos.fromBlock(this.areaWithOldGeneration.getMinBuildHeight())
            && biomeY < QuartPos.fromBlock(this.areaWithOldGeneration.getMaxBuildHeight())) {
            int i = biomeY - QuartPos.fromBlock(this.areaWithOldGeneration.getMinBuildHeight());

            for (int j = 0; j < this.biomes.size(); j++) {
                if (this.biomes.get(j) != null) {
                    Holder<Biome> holder = this.biomes.get(j).get(i);
                    if (holder != null) {
                        consumer.consume(biomeX + getX(j), biomeZ + getZ(j), holder);
                    }
                }
            }
        }
    }

    protected void iterateHeights(int biomeX, int biomeZ, BlendingData.HeightConsumer consumer) {
        for (int i = 0; i < this.heights.length; i++) {
            double d = this.heights[i];
            if (d != Double.MAX_VALUE) {
                consumer.consume(biomeX + getX(i), biomeZ + getZ(i), d);
            }
        }
    }

    protected void iterateDensities(int biomeX, int biomeZ, int minHalfSectionY, int maxHalfSectionY, BlendingData.DensityConsumer consumer) {
        int i = this.getColumnMinY();
        int j = Math.max(0, minHalfSectionY - i);
        int k = Math.min(this.cellCountPerColumn(), maxHalfSectionY - i);

        for (int l = 0; l < this.densities.length; l++) {
            double[] ds = this.densities[l];
            if (ds != null) {
                int m = biomeX + getX(l);
                int n = biomeZ + getZ(l);

                for (int o = j; o < k; o++) {
                    consumer.consume(m, o + i, n, ds[o] * 0.1);
                }
            }
        }
    }

    private int cellCountPerColumn() {
        return this.areaWithOldGeneration.getSectionsCount() * 2;
    }

    private int quartCountPerColumn() {
        return QuartPos.fromSection(this.areaWithOldGeneration.getSectionsCount());
    }

    private int getColumnMinY() {
        return this.getMinY() + 1;
    }

    private int getMinY() {
        return this.areaWithOldGeneration.getMinSection() * 2;
    }

    private int getCellYIndex(int halfSectionY) {
        return halfSectionY - this.getColumnMinY();
    }

    private static int getInsideIndex(int chunkBiomeX, int chunkBiomeZ) {
        return CELL_HORIZONTAL_MAX_INDEX_INSIDE - chunkBiomeX + chunkBiomeZ;
    }

    private static int getOutsideIndex(int chunkBiomeX, int chunkBiomeZ) {
        return CELL_COLUMN_INSIDE_COUNT + chunkBiomeX + CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - chunkBiomeZ;
    }

    private static int getX(int index) {
        if (index < CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(CELL_HORIZONTAL_MAX_INDEX_INSIDE - index);
        } else {
            int i = index - CELL_COLUMN_INSIDE_COUNT;
            return CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - i);
        }
    }

    private static int getZ(int index) {
        if (index < CELL_COLUMN_INSIDE_COUNT) {
            return zeroIfNegative(index - CELL_HORIZONTAL_MAX_INDEX_INSIDE);
        } else {
            int i = index - CELL_COLUMN_INSIDE_COUNT;
            return CELL_HORIZONTAL_MAX_INDEX_OUTSIDE - zeroIfNegative(i - CELL_HORIZONTAL_MAX_INDEX_OUTSIDE);
        }
    }

    private static int zeroIfNegative(int i) {
        return i & ~(i >> 31);
    }

    public LevelHeightAccessor getAreaWithOldGeneration() {
        return this.areaWithOldGeneration;
    }

    protected interface BiomeConsumer {
        void consume(int biomeX, int biomeZ, Holder<Biome> biome);
    }

    protected interface DensityConsumer {
        void consume(int biomeX, int halfSectionY, int biomeZ, double collidableBlockDensity);
    }

    protected interface HeightConsumer {
        void consume(int biomeX, int biomeZ, double height);
    }
}
