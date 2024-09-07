package net.minecraft.world.level.levelgen.blending;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;

public class Blender {
    private static final Blender EMPTY = new Blender(new Long2ObjectOpenHashMap(), new Long2ObjectOpenHashMap()) {
        @Override
        public Blender.BlendingOutput blendOffsetAndFactor(int blockX, int blockZ) {
            return new Blender.BlendingOutput(1.0, 0.0);
        }

        @Override
        public double blendDensity(DensityFunction.FunctionContext pos, double density) {
            return density;
        }

        @Override
        public BiomeResolver getBiomeResolver(BiomeResolver biomeSupplier) {
            return biomeSupplier;
        }
    };
    private static final NormalNoise SHIFT_NOISE = NormalNoise.create(new XoroshiroRandomSource(42L), NoiseData.DEFAULT_SHIFT);
    private static final int HEIGHT_BLENDING_RANGE_CELLS = QuartPos.fromSection(7) - 1;
    private static final int HEIGHT_BLENDING_RANGE_CHUNKS = QuartPos.toSection(HEIGHT_BLENDING_RANGE_CELLS + 3);
    private static final int DENSITY_BLENDING_RANGE_CELLS = 2;
    private static final int DENSITY_BLENDING_RANGE_CHUNKS = QuartPos.toSection(5);
    private static final double OLD_CHUNK_XZ_RADIUS = 8.0;
    private final Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData;
    private final Long2ObjectOpenHashMap<BlendingData> densityBlendingData;

    public static Blender empty() {
        return EMPTY;
    }

    public static Blender of(@Nullable WorldGenRegion chunkRegion) {
        if (chunkRegion == null) {
            return EMPTY;
        } else {
            ChunkPos chunkPos = chunkRegion.getCenter();
            if (!chunkRegion.isOldChunkAround(chunkPos, HEIGHT_BLENDING_RANGE_CHUNKS)) {
                return EMPTY;
            } else {
                Long2ObjectOpenHashMap<BlendingData> long2ObjectOpenHashMap = new Long2ObjectOpenHashMap<>();
                Long2ObjectOpenHashMap<BlendingData> long2ObjectOpenHashMap2 = new Long2ObjectOpenHashMap<>();
                int i = Mth.square(HEIGHT_BLENDING_RANGE_CHUNKS + 1);

                for (int j = -HEIGHT_BLENDING_RANGE_CHUNKS; j <= HEIGHT_BLENDING_RANGE_CHUNKS; j++) {
                    for (int k = -HEIGHT_BLENDING_RANGE_CHUNKS; k <= HEIGHT_BLENDING_RANGE_CHUNKS; k++) {
                        if (j * j + k * k <= i) {
                            int l = chunkPos.x + j;
                            int m = chunkPos.z + k;
                            BlendingData blendingData = BlendingData.getOrUpdateBlendingData(chunkRegion, l, m);
                            if (blendingData != null) {
                                long2ObjectOpenHashMap.put(ChunkPos.asLong(l, m), blendingData);
                                if (j >= -DENSITY_BLENDING_RANGE_CHUNKS
                                    && j <= DENSITY_BLENDING_RANGE_CHUNKS
                                    && k >= -DENSITY_BLENDING_RANGE_CHUNKS
                                    && k <= DENSITY_BLENDING_RANGE_CHUNKS) {
                                    long2ObjectOpenHashMap2.put(ChunkPos.asLong(l, m), blendingData);
                                }
                            }
                        }
                    }
                }

                return long2ObjectOpenHashMap.isEmpty() && long2ObjectOpenHashMap2.isEmpty()
                    ? EMPTY
                    : new Blender(long2ObjectOpenHashMap, long2ObjectOpenHashMap2);
            }
        }
    }

    Blender(Long2ObjectOpenHashMap<BlendingData> blendingData, Long2ObjectOpenHashMap<BlendingData> closeBlendingData) {
        this.heightAndBiomeBlendingData = blendingData;
        this.densityBlendingData = closeBlendingData;
    }

    public Blender.BlendingOutput blendOffsetAndFactor(int blockX, int blockZ) {
        int i = QuartPos.fromBlock(blockX);
        int j = QuartPos.fromBlock(blockZ);
        double d = this.getBlendingDataValue(i, 0, j, BlendingData::getHeight);
        if (d != Double.MAX_VALUE) {
            return new Blender.BlendingOutput(0.0, heightToOffset(d));
        } else {
            MutableDouble mutableDouble = new MutableDouble(0.0);
            MutableDouble mutableDouble2 = new MutableDouble(0.0);
            MutableDouble mutableDouble3 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.heightAndBiomeBlendingData
                .forEach(
                    (chunkPos, data) -> data.iterateHeights(
                            QuartPos.fromSection(ChunkPos.getX(chunkPos)), QuartPos.fromSection(ChunkPos.getZ(chunkPos)), (biomeX, biomeZ, height) -> {
                                double dx = Mth.length((double)(i - biomeX), (double)(j - biomeZ));
                                if (!(dx > (double)HEIGHT_BLENDING_RANGE_CELLS)) {
                                    if (dx < mutableDouble3.doubleValue()) {
                                        mutableDouble3.setValue(dx);
                                    }

                                    double ex = 1.0 / (dx * dx * dx * dx);
                                    mutableDouble2.add(height * ex);
                                    mutableDouble.add(ex);
                                }
                            }
                        )
                );
            if (mutableDouble3.doubleValue() == Double.POSITIVE_INFINITY) {
                return new Blender.BlendingOutput(1.0, 0.0);
            } else {
                double e = mutableDouble2.doubleValue() / mutableDouble.doubleValue();
                double f = Mth.clamp(mutableDouble3.doubleValue() / (double)(HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
                f = 3.0 * f * f - 2.0 * f * f * f;
                return new Blender.BlendingOutput(f, heightToOffset(e));
            }
        }
    }

    private static double heightToOffset(double height) {
        double d = 1.0;
        double e = height + 0.5;
        double f = Mth.positiveModulo(e, 8.0);
        return 1.0 * (32.0 * (e - 128.0) - 3.0 * (e - 120.0) * f + 3.0 * f * f) / (128.0 * (32.0 - 3.0 * f));
    }

    public double blendDensity(DensityFunction.FunctionContext pos, double density) {
        int i = QuartPos.fromBlock(pos.blockX());
        int j = pos.blockY() / 8;
        int k = QuartPos.fromBlock(pos.blockZ());
        double d = this.getBlendingDataValue(i, j, k, BlendingData::getDensity);
        if (d != Double.MAX_VALUE) {
            return d;
        } else {
            MutableDouble mutableDouble = new MutableDouble(0.0);
            MutableDouble mutableDouble2 = new MutableDouble(0.0);
            MutableDouble mutableDouble3 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.densityBlendingData
                .forEach(
                    (chunkPos, data) -> data.iterateDensities(
                            QuartPos.fromSection(ChunkPos.getX(chunkPos)),
                            QuartPos.fromSection(ChunkPos.getZ(chunkPos)),
                            j - 1,
                            j + 1,
                            (biomeX, halfSectionY, biomeZ, collidableBlockDensity) -> {
                                double dx = Mth.length((double)(i - biomeX), (double)((j - halfSectionY) * 2), (double)(k - biomeZ));
                                if (!(dx > 2.0)) {
                                    if (dx < mutableDouble3.doubleValue()) {
                                        mutableDouble3.setValue(dx);
                                    }

                                    double ex = 1.0 / (dx * dx * dx * dx);
                                    mutableDouble2.add(collidableBlockDensity * ex);
                                    mutableDouble.add(ex);
                                }
                            }
                        )
                );
            if (mutableDouble3.doubleValue() == Double.POSITIVE_INFINITY) {
                return density;
            } else {
                double e = mutableDouble2.doubleValue() / mutableDouble.doubleValue();
                double f = Mth.clamp(mutableDouble3.doubleValue() / 3.0, 0.0, 1.0);
                return Mth.lerp(f, e, density);
            }
        }
    }

    private double getBlendingDataValue(int biomeX, int biomeY, int biomeZ, Blender.CellValueGetter sampler) {
        int i = QuartPos.toSection(biomeX);
        int j = QuartPos.toSection(biomeZ);
        boolean bl = (biomeX & 3) == 0;
        boolean bl2 = (biomeZ & 3) == 0;
        double d = this.getBlendingDataValue(sampler, i, j, biomeX, biomeY, biomeZ);
        if (d == Double.MAX_VALUE) {
            if (bl && bl2) {
                d = this.getBlendingDataValue(sampler, i - 1, j - 1, biomeX, biomeY, biomeZ);
            }

            if (d == Double.MAX_VALUE) {
                if (bl) {
                    d = this.getBlendingDataValue(sampler, i - 1, j, biomeX, biomeY, biomeZ);
                }

                if (d == Double.MAX_VALUE && bl2) {
                    d = this.getBlendingDataValue(sampler, i, j - 1, biomeX, biomeY, biomeZ);
                }
            }
        }

        return d;
    }

    private double getBlendingDataValue(Blender.CellValueGetter sampler, int chunkX, int chunkZ, int biomeX, int biomeY, int biomeZ) {
        BlendingData blendingData = this.heightAndBiomeBlendingData.get(ChunkPos.asLong(chunkX, chunkZ));
        return blendingData != null
            ? sampler.get(blendingData, biomeX - QuartPos.fromSection(chunkX), biomeY, biomeZ - QuartPos.fromSection(chunkZ))
            : Double.MAX_VALUE;
    }

    public BiomeResolver getBiomeResolver(BiomeResolver biomeSupplier) {
        return (x, y, z, noise) -> {
            Holder<Biome> holder = this.blendBiome(x, y, z);
            return holder == null ? biomeSupplier.getNoiseBiome(x, y, z, noise) : holder;
        };
    }

    @Nullable
    private Holder<Biome> blendBiome(int x, int y, int z) {
        MutableDouble mutableDouble = new MutableDouble(Double.POSITIVE_INFINITY);
        MutableObject<Holder<Biome>> mutableObject = new MutableObject<>();
        this.heightAndBiomeBlendingData
            .forEach(
                (chunkPos, data) -> data.iterateBiomes(
                        QuartPos.fromSection(ChunkPos.getX(chunkPos)), y, QuartPos.fromSection(ChunkPos.getZ(chunkPos)), (biomeX, biomeZ, biome) -> {
                            double dx = Mth.length((double)(x - biomeX), (double)(z - biomeZ));
                            if (!(dx > (double)HEIGHT_BLENDING_RANGE_CELLS)) {
                                if (dx < mutableDouble.doubleValue()) {
                                    mutableObject.setValue(biome);
                                    mutableDouble.setValue(dx);
                                }
                            }
                        }
                    )
            );
        if (mutableDouble.doubleValue() == Double.POSITIVE_INFINITY) {
            return null;
        } else {
            double d = SHIFT_NOISE.getValue((double)x, 0.0, (double)z) * 12.0;
            double e = Mth.clamp((mutableDouble.doubleValue() + d) / (double)(HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0, 1.0);
            return e > 0.5 ? null : mutableObject.getValue();
        }
    }

    public static void generateBorderTicks(WorldGenRegion chunkRegion, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        boolean bl = chunk.isOldNoiseGeneration();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
        BlendingData blendingData = chunk.getBlendingData();
        if (blendingData != null) {
            int i = blendingData.getAreaWithOldGeneration().getMinBuildHeight();
            int j = blendingData.getAreaWithOldGeneration().getMaxBuildHeight() - 1;
            if (bl) {
                for (int k = 0; k < 16; k++) {
                    for (int l = 0; l < 16; l++) {
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, i - 1, l));
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, i, l));
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, j, l));
                        generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, k, j + 1, l));
                    }
                }
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (chunkRegion.getChunk(chunkPos.x + direction.getStepX(), chunkPos.z + direction.getStepZ()).isOldNoiseGeneration() != bl) {
                    int m = direction == Direction.EAST ? 15 : 0;
                    int n = direction == Direction.WEST ? 0 : 15;
                    int o = direction == Direction.SOUTH ? 15 : 0;
                    int p = direction == Direction.NORTH ? 0 : 15;

                    for (int q = m; q <= n; q++) {
                        for (int r = o; r <= p; r++) {
                            int s = Math.min(j, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, q, r)) + 1;

                            for (int t = i; t < s; t++) {
                                generateBorderTick(chunk, mutableBlockPos.setWithOffset(blockPos, q, t, r));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateBorderTick(ChunkAccess chunk, BlockPos pos) {
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.is(BlockTags.LEAVES)) {
            chunk.markPosForPostprocessing(pos);
        }

        FluidState fluidState = chunk.getFluidState(pos);
        if (!fluidState.isEmpty()) {
            chunk.markPosForPostprocessing(pos);
        }
    }

    public static void addAroundOldChunksCarvingMaskFilter(WorldGenLevel world, ProtoChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        Builder<Direction8, BlendingData> builder = ImmutableMap.builder();

        for (Direction8 direction8 : Direction8.values()) {
            int i = chunkPos.x + direction8.getStepX();
            int j = chunkPos.z + direction8.getStepZ();
            BlendingData blendingData = world.getChunk(i, j).getBlendingData();
            if (blendingData != null) {
                builder.put(direction8, blendingData);
            }
        }

        ImmutableMap<Direction8, BlendingData> immutableMap = builder.build();
        if (chunk.isOldNoiseGeneration() || !immutableMap.isEmpty()) {
            Blender.DistanceGetter distanceGetter = makeOldChunkDistanceGetter(chunk.getBlendingData(), immutableMap);
            CarvingMask.Mask mask = (offsetX, y, offsetZ) -> {
                double d = (double)offsetX + 0.5 + SHIFT_NOISE.getValue((double)offsetX, (double)y, (double)offsetZ) * 4.0;
                double e = (double)y + 0.5 + SHIFT_NOISE.getValue((double)y, (double)offsetZ, (double)offsetX) * 4.0;
                double f = (double)offsetZ + 0.5 + SHIFT_NOISE.getValue((double)offsetZ, (double)offsetX, (double)y) * 4.0;
                return distanceGetter.getDistance(d, e, f) < 4.0;
            };
            Stream.of(GenerationStep.Carving.values()).map(chunk::getOrCreateCarvingMask).forEach(maskx -> maskx.setAdditionalMask(mask));
        }
    }

    public static Blender.DistanceGetter makeOldChunkDistanceGetter(@Nullable BlendingData data, Map<Direction8, BlendingData> neighborData) {
        List<Blender.DistanceGetter> list = Lists.newArrayList();
        if (data != null) {
            list.add(makeOffsetOldChunkDistanceGetter(null, data));
        }

        neighborData.forEach((direction, datax) -> list.add(makeOffsetOldChunkDistanceGetter(direction, datax)));
        return (offsetX, y, offsetZ) -> {
            double d = Double.POSITIVE_INFINITY;

            for (Blender.DistanceGetter distanceGetter : list) {
                double e = distanceGetter.getDistance(offsetX, y, offsetZ);
                if (e < d) {
                    d = e;
                }
            }

            return d;
        };
    }

    private static Blender.DistanceGetter makeOffsetOldChunkDistanceGetter(@Nullable Direction8 direction, BlendingData data) {
        double d = 0.0;
        double e = 0.0;
        if (direction != null) {
            for (Direction direction2 : direction.getDirections()) {
                d += (double)(direction2.getStepX() * 16);
                e += (double)(direction2.getStepZ() * 16);
            }
        }

        double f = d;
        double g = e;
        double h = (double)data.getAreaWithOldGeneration().getHeight() / 2.0;
        double i = (double)data.getAreaWithOldGeneration().getMinBuildHeight() + h;
        return (offsetX, y, offsetZ) -> distanceToCube(offsetX - 8.0 - f, y - i, offsetZ - 8.0 - g, 8.0, h, 8.0);
    }

    private static double distanceToCube(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d = Math.abs(x1) - x2;
        double e = Math.abs(y1) - y2;
        double f = Math.abs(z1) - z2;
        return Mth.length(Math.max(0.0, d), Math.max(0.0, e), Math.max(0.0, f));
    }

    public static record BlendingOutput(double alpha, double blendingOffset) {
    }

    interface CellValueGetter {
        double get(BlendingData data, int biomeX, int biomeY, int biomeZ);
    }

    public interface DistanceGetter {
        double getDistance(double offsetX, double y, double offsetZ);
    }
}
