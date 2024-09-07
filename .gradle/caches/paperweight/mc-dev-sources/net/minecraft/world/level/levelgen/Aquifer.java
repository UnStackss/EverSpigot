package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import org.apache.commons.lang3.mutable.MutableDouble;

public interface Aquifer {
    static Aquifer create(
        NoiseChunk chunkNoiseSampler,
        ChunkPos chunkPos,
        NoiseRouter noiseRouter,
        PositionalRandomFactory randomSplitter,
        int minimumY,
        int height,
        Aquifer.FluidPicker fluidLevelSampler
    ) {
        return new Aquifer.NoiseBasedAquifer(chunkNoiseSampler, chunkPos, noiseRouter, randomSplitter, minimumY, height, fluidLevelSampler);
    }

    static Aquifer createDisabled(Aquifer.FluidPicker fluidLevelSampler) {
        return new Aquifer() {
            @Nullable
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext pos, double density) {
                return density > 0.0 ? null : fluidLevelSampler.computeFluid(pos.blockX(), pos.blockY(), pos.blockZ()).at(pos.blockY());
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    @Nullable
    BlockState computeSubstance(DensityFunction.FunctionContext pos, double density);

    boolean shouldScheduleFluidUpdate();

    public interface FluidPicker {
        Aquifer.FluidStatus computeFluid(int x, int y, int z);
    }

    public static final class FluidStatus {
        final int fluidLevel;
        final BlockState fluidType;

        public FluidStatus(int y, BlockState state) {
            this.fluidLevel = y;
            this.fluidType = state;
        }

        public BlockState at(int y) {
            return y < this.fluidLevel ? this.fluidType : Blocks.AIR.defaultBlockState();
        }
    }

    public static class NoiseBasedAquifer implements Aquifer {
        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SEPARATION = 6;
        private static final int Y_SEPARATION = 3;
        private static final int Z_SEPARATION = 6;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int MAX_REASONABLE_DISTANCE_TO_AQUIFER_CENTER = 11;
        private static final double FLOWING_UPDATE_SIMULARITY = similarity(Mth.square(10), Mth.square(12));
        private final NoiseChunk noiseChunk;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final Aquifer.FluidStatus[] aquiferCache;
        private final long[] aquiferLocationCache;
        private final Aquifer.FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final int minGridX;
        private final int minGridY;
        private final int minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][]{
            {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
        };

        NoiseBasedAquifer(
            NoiseChunk chunkNoiseSampler,
            ChunkPos chunkPos,
            NoiseRouter noiseRouter,
            PositionalRandomFactory randomSplitter,
            int minimumY,
            int height,
            Aquifer.FluidPicker fluidLevelSampler
        ) {
            this.noiseChunk = chunkNoiseSampler;
            this.barrierNoise = noiseRouter.barrierNoise();
            this.fluidLevelFloodednessNoise = noiseRouter.fluidLevelFloodednessNoise();
            this.fluidLevelSpreadNoise = noiseRouter.fluidLevelSpreadNoise();
            this.lavaNoise = noiseRouter.lavaNoise();
            this.erosion = noiseRouter.erosion();
            this.depth = noiseRouter.depth();
            this.positionalRandomFactory = randomSplitter;
            this.minGridX = this.gridX(chunkPos.getMinBlockX()) - 1;
            this.globalFluidPicker = fluidLevelSampler;
            int i = this.gridX(chunkPos.getMaxBlockX()) + 1;
            this.gridSizeX = i - this.minGridX + 1;
            this.minGridY = this.gridY(minimumY) - 1;
            int j = this.gridY(minimumY + height) + 1;
            int k = j - this.minGridY + 1;
            this.minGridZ = this.gridZ(chunkPos.getMinBlockZ()) - 1;
            int l = this.gridZ(chunkPos.getMaxBlockZ()) + 1;
            this.gridSizeZ = l - this.minGridZ + 1;
            int m = this.gridSizeX * k * this.gridSizeZ;
            this.aquiferCache = new Aquifer.FluidStatus[m];
            this.aquiferLocationCache = new long[m];
            Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
        }

        private int getIndex(int x, int y, int z) {
            int i = x - this.minGridX;
            int j = y - this.minGridY;
            int k = z - this.minGridZ;
            return (j * this.gridSizeZ + k) * this.gridSizeX + i;
        }

        @Nullable
        @Override
        public BlockState computeSubstance(DensityFunction.FunctionContext pos, double density) {
            int i = pos.blockX();
            int j = pos.blockY();
            int k = pos.blockZ();
            if (density > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            } else {
                Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(i, j, k);
                if (fluidStatus.at(j).is(Blocks.LAVA)) {
                    this.shouldScheduleFluidUpdate = false;
                    return Blocks.LAVA.defaultBlockState();
                } else {
                    int l = Math.floorDiv(i - 5, 16);
                    int m = Math.floorDiv(j + 1, 12);
                    int n = Math.floorDiv(k - 5, 16);
                    int o = Integer.MAX_VALUE;
                    int p = Integer.MAX_VALUE;
                    int q = Integer.MAX_VALUE;
                    long r = 0L;
                    long s = 0L;
                    long t = 0L;

                    for (int u = 0; u <= 1; u++) {
                        for (int v = -1; v <= 1; v++) {
                            for (int w = 0; w <= 1; w++) {
                                int x = l + u;
                                int y = m + v;
                                int z = n + w;
                                int aa = this.getIndex(x, y, z);
                                long ab = this.aquiferLocationCache[aa];
                                long ac;
                                if (ab != Long.MAX_VALUE) {
                                    ac = ab;
                                } else {
                                    RandomSource randomSource = this.positionalRandomFactory.at(x, y, z);
                                    ac = BlockPos.asLong(x * 16 + randomSource.nextInt(10), y * 12 + randomSource.nextInt(9), z * 16 + randomSource.nextInt(10));
                                    this.aquiferLocationCache[aa] = ac;
                                }

                                int ae = BlockPos.getX(ac) - i;
                                int af = BlockPos.getY(ac) - j;
                                int ag = BlockPos.getZ(ac) - k;
                                int ah = ae * ae + af * af + ag * ag;
                                if (o >= ah) {
                                    t = s;
                                    s = r;
                                    r = ac;
                                    q = p;
                                    p = o;
                                    o = ah;
                                } else if (p >= ah) {
                                    t = s;
                                    s = ac;
                                    q = p;
                                    p = ah;
                                } else if (q >= ah) {
                                    t = ac;
                                    q = ah;
                                }
                            }
                        }
                    }

                    Aquifer.FluidStatus fluidStatus2 = this.getAquiferStatus(r);
                    double d = similarity(o, p);
                    BlockState blockState = fluidStatus2.at(j);
                    if (d <= 0.0) {
                        this.shouldScheduleFluidUpdate = d >= FLOWING_UPDATE_SIMULARITY;
                        return blockState;
                    } else if (blockState.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
                        this.shouldScheduleFluidUpdate = true;
                        return blockState;
                    } else {
                        MutableDouble mutableDouble = new MutableDouble(Double.NaN);
                        Aquifer.FluidStatus fluidStatus3 = this.getAquiferStatus(s);
                        double e = d * this.calculatePressure(pos, mutableDouble, fluidStatus2, fluidStatus3);
                        if (density + e > 0.0) {
                            this.shouldScheduleFluidUpdate = false;
                            return null;
                        } else {
                            Aquifer.FluidStatus fluidStatus4 = this.getAquiferStatus(t);
                            double f = similarity(o, q);
                            if (f > 0.0) {
                                double g = d * f * this.calculatePressure(pos, mutableDouble, fluidStatus2, fluidStatus4);
                                if (density + g > 0.0) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            double h = similarity(p, q);
                            if (h > 0.0) {
                                double ai = d * h * this.calculatePressure(pos, mutableDouble, fluidStatus3, fluidStatus4);
                                if (density + ai > 0.0) {
                                    this.shouldScheduleFluidUpdate = false;
                                    return null;
                                }
                            }

                            this.shouldScheduleFluidUpdate = true;
                            return blockState;
                        }
                    }
                }
            }
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private static double similarity(int i, int a) {
            double d = 25.0;
            return 1.0 - (double)Math.abs(a - i) / 25.0;
        }

        private double calculatePressure(
            DensityFunction.FunctionContext pos, MutableDouble mutableDouble, Aquifer.FluidStatus fluidStatus, Aquifer.FluidStatus fluidStatus2
        ) {
            int i = pos.blockY();
            BlockState blockState = fluidStatus.at(i);
            BlockState blockState2 = fluidStatus2.at(i);
            if ((!blockState.is(Blocks.LAVA) || !blockState2.is(Blocks.WATER)) && (!blockState.is(Blocks.WATER) || !blockState2.is(Blocks.LAVA))) {
                int j = Math.abs(fluidStatus.fluidLevel - fluidStatus2.fluidLevel);
                if (j == 0) {
                    return 0.0;
                } else {
                    double d = 0.5 * (double)(fluidStatus.fluidLevel + fluidStatus2.fluidLevel);
                    double e = (double)i + 0.5 - d;
                    double f = (double)j / 2.0;
                    double g = 0.0;
                    double h = 2.5;
                    double k = 1.5;
                    double l = 3.0;
                    double m = 10.0;
                    double n = 3.0;
                    double o = f - Math.abs(e);
                    double q;
                    if (e > 0.0) {
                        double p = 0.0 + o;
                        if (p > 0.0) {
                            q = p / 1.5;
                        } else {
                            q = p / 2.5;
                        }
                    } else {
                        double s = 3.0 + o;
                        if (s > 0.0) {
                            q = s / 3.0;
                        } else {
                            q = s / 10.0;
                        }
                    }

                    double v = 2.0;
                    double z;
                    if (!(q < -2.0) && !(q > 2.0)) {
                        double x = mutableDouble.getValue();
                        if (Double.isNaN(x)) {
                            double y = this.barrierNoise.compute(pos);
                            mutableDouble.setValue(y);
                            z = y;
                        } else {
                            z = x;
                        }
                    } else {
                        z = 0.0;
                    }

                    return 2.0 * (z + q);
                }
            } else {
                return 2.0;
            }
        }

        private int gridX(int x) {
            return Math.floorDiv(x, 16);
        }

        private int gridY(int y) {
            return Math.floorDiv(y, 12);
        }

        private int gridZ(int z) {
            return Math.floorDiv(z, 16);
        }

        private Aquifer.FluidStatus getAquiferStatus(long pos) {
            int i = BlockPos.getX(pos);
            int j = BlockPos.getY(pos);
            int k = BlockPos.getZ(pos);
            int l = this.gridX(i);
            int m = this.gridY(j);
            int n = this.gridZ(k);
            int o = this.getIndex(l, m, n);
            Aquifer.FluidStatus fluidStatus = this.aquiferCache[o];
            if (fluidStatus != null) {
                return fluidStatus;
            } else {
                Aquifer.FluidStatus fluidStatus2 = this.computeFluid(i, j, k);
                this.aquiferCache[o] = fluidStatus2;
                return fluidStatus2;
            }
        }

        private Aquifer.FluidStatus computeFluid(int blockX, int blockY, int blockZ) {
            Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(blockX, blockY, blockZ);
            int i = Integer.MAX_VALUE;
            int j = blockY + 12;
            int k = blockY - 12;
            boolean bl = false;

            for (int[] is : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int l = blockX + SectionPos.sectionToBlockCoord(is[0]);
                int m = blockZ + SectionPos.sectionToBlockCoord(is[1]);
                int n = this.noiseChunk.preliminarySurfaceLevel(l, m);
                int o = n + 8;
                boolean bl2 = is[0] == 0 && is[1] == 0;
                if (bl2 && k > o) {
                    return fluidStatus;
                }

                boolean bl3 = j > o;
                if (bl3 || bl2) {
                    Aquifer.FluidStatus fluidStatus2 = this.globalFluidPicker.computeFluid(l, o, m);
                    if (!fluidStatus2.at(o).isAir()) {
                        if (bl2) {
                            bl = true;
                        }

                        if (bl3) {
                            return fluidStatus2;
                        }
                    }
                }

                i = Math.min(i, n);
            }

            int p = this.computeSurfaceLevel(blockX, blockY, blockZ, fluidStatus, i, bl);
            return new Aquifer.FluidStatus(p, this.computeFluidType(blockX, blockY, blockZ, fluidStatus, p));
        }

        private int computeSurfaceLevel(int blockX, int blockY, int blockZ, Aquifer.FluidStatus defaultFluidLevel, int surfaceHeightEstimate, boolean bl) {
            DensityFunction.SinglePointContext singlePointContext = new DensityFunction.SinglePointContext(blockX, blockY, blockZ);
            double d;
            double e;
            if (OverworldBiomeBuilder.isDeepDarkRegion(this.erosion, this.depth, singlePointContext)) {
                d = -1.0;
                e = -1.0;
            } else {
                int i = surfaceHeightEstimate + 8 - blockY;
                int j = 64;
                double f = bl ? Mth.clampedMap((double)i, 0.0, 64.0, 1.0, 0.0) : 0.0;
                double g = Mth.clamp(this.fluidLevelFloodednessNoise.compute(singlePointContext), -1.0, 1.0);
                double h = Mth.map(f, 1.0, 0.0, -0.3, 0.8);
                double k = Mth.map(f, 1.0, 0.0, -0.8, 0.4);
                d = g - k;
                e = g - h;
            }

            int n;
            if (e > 0.0) {
                n = defaultFluidLevel.fluidLevel;
            } else if (d > 0.0) {
                n = this.computeRandomizedFluidSurfaceLevel(blockX, blockY, blockZ, surfaceHeightEstimate);
            } else {
                n = DimensionType.WAY_BELOW_MIN_Y;
            }

            return n;
        }

        private int computeRandomizedFluidSurfaceLevel(int blockX, int blockY, int blockZ, int surfaceHeightEstimate) {
            int i = 16;
            int j = 40;
            int k = Math.floorDiv(blockX, 16);
            int l = Math.floorDiv(blockY, 40);
            int m = Math.floorDiv(blockZ, 16);
            int n = l * 40 + 20;
            int o = 10;
            double d = this.fluidLevelSpreadNoise.compute(new DensityFunction.SinglePointContext(k, l, m)) * 10.0;
            int p = Mth.quantize(d, 3);
            int q = n + p;
            return Math.min(surfaceHeightEstimate, q);
        }

        private BlockState computeFluidType(int blockX, int blockY, int blockZ, Aquifer.FluidStatus defaultFluidLevel, int fluidLevel) {
            BlockState blockState = defaultFluidLevel.fluidType;
            if (fluidLevel <= -10 && fluidLevel != DimensionType.WAY_BELOW_MIN_Y && defaultFluidLevel.fluidType != Blocks.LAVA.defaultBlockState()) {
                int i = 64;
                int j = 40;
                int k = Math.floorDiv(blockX, 64);
                int l = Math.floorDiv(blockY, 40);
                int m = Math.floorDiv(blockZ, 64);
                double d = this.lavaNoise.compute(new DensityFunction.SinglePointContext(k, l, m));
                if (Math.abs(d) > 0.3) {
                    blockState = Blocks.LAVA.defaultBlockState();
                }
            }

            return blockState;
        }
    }
}
