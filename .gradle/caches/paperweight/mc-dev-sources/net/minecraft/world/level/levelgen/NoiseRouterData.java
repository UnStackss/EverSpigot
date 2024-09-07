package net.minecraft.world.level.levelgen;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseRouterData {
    public static final float GLOBAL_OFFSET = -0.50375F;
    private static final float ORE_THICKNESS = 0.08F;
    private static final double VEININESS_FREQUENCY = 1.5;
    private static final double NOODLE_SPACING_AND_STRAIGHTNESS = 1.5;
    private static final double SURFACE_DENSITY_THRESHOLD = 1.5625;
    private static final double CHEESE_NOISE_TARGET = -0.703125;
    public static final int ISLAND_CHUNK_DISTANCE = 64;
    public static final long ISLAND_CHUNK_DISTANCE_SQR = 4096L;
    private static final DensityFunction BLENDING_FACTOR = DensityFunctions.constant(10.0);
    private static final DensityFunction BLENDING_JAGGEDNESS = DensityFunctions.zero();
    private static final ResourceKey<DensityFunction> ZERO = createKey("zero");
    private static final ResourceKey<DensityFunction> Y = createKey("y");
    private static final ResourceKey<DensityFunction> SHIFT_X = createKey("shift_x");
    private static final ResourceKey<DensityFunction> SHIFT_Z = createKey("shift_z");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_OVERWORLD = createKey("overworld/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_NETHER = createKey("nether/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_END = createKey("end/base_3d_noise");
    public static final ResourceKey<DensityFunction> CONTINENTS = createKey("overworld/continents");
    public static final ResourceKey<DensityFunction> EROSION = createKey("overworld/erosion");
    public static final ResourceKey<DensityFunction> RIDGES = createKey("overworld/ridges");
    public static final ResourceKey<DensityFunction> RIDGES_FOLDED = createKey("overworld/ridges_folded");
    public static final ResourceKey<DensityFunction> OFFSET = createKey("overworld/offset");
    public static final ResourceKey<DensityFunction> FACTOR = createKey("overworld/factor");
    public static final ResourceKey<DensityFunction> JAGGEDNESS = createKey("overworld/jaggedness");
    public static final ResourceKey<DensityFunction> DEPTH = createKey("overworld/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE = createKey("overworld/sloped_cheese");
    public static final ResourceKey<DensityFunction> CONTINENTS_LARGE = createKey("overworld_large_biomes/continents");
    public static final ResourceKey<DensityFunction> EROSION_LARGE = createKey("overworld_large_biomes/erosion");
    private static final ResourceKey<DensityFunction> OFFSET_LARGE = createKey("overworld_large_biomes/offset");
    private static final ResourceKey<DensityFunction> FACTOR_LARGE = createKey("overworld_large_biomes/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_LARGE = createKey("overworld_large_biomes/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_LARGE = createKey("overworld_large_biomes/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_LARGE = createKey("overworld_large_biomes/sloped_cheese");
    private static final ResourceKey<DensityFunction> OFFSET_AMPLIFIED = createKey("overworld_amplified/offset");
    private static final ResourceKey<DensityFunction> FACTOR_AMPLIFIED = createKey("overworld_amplified/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_AMPLIFIED = createKey("overworld_amplified/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_AMPLIFIED = createKey("overworld_amplified/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_AMPLIFIED = createKey("overworld_amplified/sloped_cheese");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_END = createKey("end/sloped_cheese");
    private static final ResourceKey<DensityFunction> SPAGHETTI_ROUGHNESS_FUNCTION = createKey("overworld/caves/spaghetti_roughness_function");
    private static final ResourceKey<DensityFunction> ENTRANCES = createKey("overworld/caves/entrances");
    private static final ResourceKey<DensityFunction> NOODLE = createKey("overworld/caves/noodle");
    private static final ResourceKey<DensityFunction> PILLARS = createKey("overworld/caves/pillars");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D_THICKNESS_MODULATOR = createKey("overworld/caves/spaghetti_2d_thickness_modulator");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D = createKey("overworld/caves/spaghetti_2d");

    private static ResourceKey<DensityFunction> createKey(String id) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace(id));
    }

    public static Holder<? extends DensityFunction> bootstrap(BootstrapContext<DensityFunction> densityFunctionRegisterable) {
        HolderGetter<NormalNoise.NoiseParameters> holderGetter = densityFunctionRegisterable.lookup(Registries.NOISE);
        HolderGetter<DensityFunction> holderGetter2 = densityFunctionRegisterable.lookup(Registries.DENSITY_FUNCTION);
        densityFunctionRegisterable.register(ZERO, DensityFunctions.zero());
        int i = DimensionType.MIN_Y * 2;
        int j = DimensionType.MAX_Y * 2;
        densityFunctionRegisterable.register(Y, DensityFunctions.yClampedGradient(i, j, (double)i, (double)j));
        DensityFunction densityFunction = registerAndWrap(
            densityFunctionRegisterable,
            SHIFT_X,
            DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftA(holderGetter.getOrThrow(Noises.SHIFT))))
        );
        DensityFunction densityFunction2 = registerAndWrap(
            densityFunctionRegisterable,
            SHIFT_Z,
            DensityFunctions.flatCache(DensityFunctions.cache2d(DensityFunctions.shiftB(holderGetter.getOrThrow(Noises.SHIFT))))
        );
        densityFunctionRegisterable.register(BASE_3D_NOISE_OVERWORLD, BlendedNoise.createUnseeded(0.25, 0.125, 80.0, 160.0, 8.0));
        densityFunctionRegisterable.register(BASE_3D_NOISE_NETHER, BlendedNoise.createUnseeded(0.25, 0.375, 80.0, 60.0, 8.0));
        densityFunctionRegisterable.register(BASE_3D_NOISE_END, BlendedNoise.createUnseeded(0.25, 0.25, 80.0, 160.0, 4.0));
        Holder<DensityFunction> holder = densityFunctionRegisterable.register(
            CONTINENTS,
            DensityFunctions.flatCache(
                DensityFunctions.shiftedNoise2d(densityFunction, densityFunction2, 0.25, holderGetter.getOrThrow(Noises.CONTINENTALNESS))
            )
        );
        Holder<DensityFunction> holder2 = densityFunctionRegisterable.register(
            EROSION,
            DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityFunction, densityFunction2, 0.25, holderGetter.getOrThrow(Noises.EROSION)))
        );
        DensityFunction densityFunction3 = registerAndWrap(
            densityFunctionRegisterable,
            RIDGES,
            DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityFunction, densityFunction2, 0.25, holderGetter.getOrThrow(Noises.RIDGE)))
        );
        densityFunctionRegisterable.register(RIDGES_FOLDED, peaksAndValleys(densityFunction3));
        DensityFunction densityFunction4 = DensityFunctions.noise(holderGetter.getOrThrow(Noises.JAGGED), 1500.0, 0.0);
        registerTerrainNoises(
            densityFunctionRegisterable, holderGetter2, densityFunction4, holder, holder2, OFFSET, FACTOR, JAGGEDNESS, DEPTH, SLOPED_CHEESE, false
        );
        Holder<DensityFunction> holder3 = densityFunctionRegisterable.register(
            CONTINENTS_LARGE,
            DensityFunctions.flatCache(
                DensityFunctions.shiftedNoise2d(densityFunction, densityFunction2, 0.25, holderGetter.getOrThrow(Noises.CONTINENTALNESS_LARGE))
            )
        );
        Holder<DensityFunction> holder4 = densityFunctionRegisterable.register(
            EROSION_LARGE,
            DensityFunctions.flatCache(DensityFunctions.shiftedNoise2d(densityFunction, densityFunction2, 0.25, holderGetter.getOrThrow(Noises.EROSION_LARGE)))
        );
        registerTerrainNoises(
            densityFunctionRegisterable,
            holderGetter2,
            densityFunction4,
            holder3,
            holder4,
            OFFSET_LARGE,
            FACTOR_LARGE,
            JAGGEDNESS_LARGE,
            DEPTH_LARGE,
            SLOPED_CHEESE_LARGE,
            false
        );
        registerTerrainNoises(
            densityFunctionRegisterable,
            holderGetter2,
            densityFunction4,
            holder,
            holder2,
            OFFSET_AMPLIFIED,
            FACTOR_AMPLIFIED,
            JAGGEDNESS_AMPLIFIED,
            DEPTH_AMPLIFIED,
            SLOPED_CHEESE_AMPLIFIED,
            true
        );
        densityFunctionRegisterable.register(
            SLOPED_CHEESE_END, DensityFunctions.add(DensityFunctions.endIslands(0L), getFunction(holderGetter2, BASE_3D_NOISE_END))
        );
        densityFunctionRegisterable.register(SPAGHETTI_ROUGHNESS_FUNCTION, spaghettiRoughnessFunction(holderGetter));
        densityFunctionRegisterable.register(
            SPAGHETTI_2D_THICKNESS_MODULATOR,
            DensityFunctions.cacheOnce(DensityFunctions.mappedNoise(holderGetter.getOrThrow(Noises.SPAGHETTI_2D_THICKNESS), 2.0, 1.0, -0.6, -1.3))
        );
        densityFunctionRegisterable.register(SPAGHETTI_2D, spaghetti2D(holderGetter2, holderGetter));
        densityFunctionRegisterable.register(ENTRANCES, entrances(holderGetter2, holderGetter));
        densityFunctionRegisterable.register(NOODLE, noodle(holderGetter2, holderGetter));
        return densityFunctionRegisterable.register(PILLARS, pillars(holderGetter));
    }

    private static void registerTerrainNoises(
        BootstrapContext<DensityFunction> densityFunctionRegisterable,
        HolderGetter<DensityFunction> densityFunctionLookup,
        DensityFunction jaggedNoise,
        Holder<DensityFunction> continents,
        Holder<DensityFunction> erosion,
        ResourceKey<DensityFunction> offsetKey,
        ResourceKey<DensityFunction> factorKey,
        ResourceKey<DensityFunction> jaggednessKey,
        ResourceKey<DensityFunction> depthKey,
        ResourceKey<DensityFunction> slopedCheeseKey,
        boolean amplified
    ) {
        DensityFunctions.Spline.Coordinate coordinate = new DensityFunctions.Spline.Coordinate(continents);
        DensityFunctions.Spline.Coordinate coordinate2 = new DensityFunctions.Spline.Coordinate(erosion);
        DensityFunctions.Spline.Coordinate coordinate3 = new DensityFunctions.Spline.Coordinate(densityFunctionLookup.getOrThrow(RIDGES));
        DensityFunctions.Spline.Coordinate coordinate4 = new DensityFunctions.Spline.Coordinate(densityFunctionLookup.getOrThrow(RIDGES_FOLDED));
        DensityFunction densityFunction = registerAndWrap(
            densityFunctionRegisterable,
            offsetKey,
            splineWithBlending(
                DensityFunctions.add(
                    DensityFunctions.constant(-0.50375F),
                    DensityFunctions.spline(TerrainProvider.overworldOffset(coordinate, coordinate2, coordinate4, amplified))
                ),
                DensityFunctions.blendOffset()
            )
        );
        DensityFunction densityFunction2 = registerAndWrap(
            densityFunctionRegisterable,
            factorKey,
            splineWithBlending(
                DensityFunctions.spline(TerrainProvider.overworldFactor(coordinate, coordinate2, coordinate3, coordinate4, amplified)), BLENDING_FACTOR
            )
        );
        DensityFunction densityFunction3 = registerAndWrap(
            densityFunctionRegisterable, depthKey, DensityFunctions.add(DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5), densityFunction)
        );
        DensityFunction densityFunction4 = registerAndWrap(
            densityFunctionRegisterable,
            jaggednessKey,
            splineWithBlending(
                DensityFunctions.spline(TerrainProvider.overworldJaggedness(coordinate, coordinate2, coordinate3, coordinate4, amplified)), BLENDING_JAGGEDNESS
            )
        );
        DensityFunction densityFunction5 = DensityFunctions.mul(densityFunction4, jaggedNoise.halfNegative());
        DensityFunction densityFunction6 = noiseGradientDensity(densityFunction2, DensityFunctions.add(densityFunction3, densityFunction5));
        densityFunctionRegisterable.register(
            slopedCheeseKey, DensityFunctions.add(densityFunction6, getFunction(densityFunctionLookup, BASE_3D_NOISE_OVERWORLD))
        );
    }

    private static DensityFunction registerAndWrap(
        BootstrapContext<DensityFunction> densityFunctionRegisterable, ResourceKey<DensityFunction> key, DensityFunction densityFunction
    ) {
        return new DensityFunctions.HolderHolder(densityFunctionRegisterable.register(key, densityFunction));
    }

    private static DensityFunction getFunction(HolderGetter<DensityFunction> densityFunctionRegisterable, ResourceKey<DensityFunction> key) {
        return new DensityFunctions.HolderHolder(densityFunctionRegisterable.getOrThrow(key));
    }

    private static DensityFunction peaksAndValleys(DensityFunction input) {
        return DensityFunctions.mul(
            DensityFunctions.add(
                DensityFunctions.add(input.abs(), DensityFunctions.constant(-0.6666666666666666)).abs(), DensityFunctions.constant(-0.3333333333333333)
            ),
            DensityFunctions.constant(-3.0)
        );
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    private static DensityFunction spaghettiRoughnessFunction(HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup) {
        DensityFunction densityFunction = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_ROUGHNESS));
        DensityFunction densityFunction2 = DensityFunctions.mappedNoise(noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_ROUGHNESS_MODULATOR), 0.0, -0.1);
        return DensityFunctions.cacheOnce(DensityFunctions.mul(densityFunction2, DensityFunctions.add(densityFunction.abs(), DensityFunctions.constant(-0.4))));
    }

    private static DensityFunction entrances(
        HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup
    ) {
        DensityFunction densityFunction = DensityFunctions.cacheOnce(
            DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_3D_RARITY), 2.0, 1.0)
        );
        DensityFunction densityFunction2 = DensityFunctions.mappedNoise(noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_3D_THICKNESS), -0.065, -0.088);
        DensityFunction densityFunction3 = DensityFunctions.weirdScaledSampler(
            densityFunction, noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_3D_1), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1
        );
        DensityFunction densityFunction4 = DensityFunctions.weirdScaledSampler(
            densityFunction, noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_3D_2), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1
        );
        DensityFunction densityFunction5 = DensityFunctions.add(DensityFunctions.max(densityFunction3, densityFunction4), densityFunction2).clamp(-1.0, 1.0);
        DensityFunction densityFunction6 = getFunction(densityFunctionLookup, SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityFunction7 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.CAVE_ENTRANCE), 0.75, 0.5);
        DensityFunction densityFunction8 = DensityFunctions.add(
            DensityFunctions.add(densityFunction7, DensityFunctions.constant(0.37)), DensityFunctions.yClampedGradient(-10, 30, 0.3, 0.0)
        );
        return DensityFunctions.cacheOnce(DensityFunctions.min(densityFunction8, DensityFunctions.add(densityFunction6, densityFunction5)));
    }

    private static DensityFunction noodle(HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup) {
        DensityFunction densityFunction = getFunction(densityFunctionLookup, Y);
        int i = -64;
        int j = -60;
        int k = 320;
        DensityFunction densityFunction2 = yLimitedInterpolatable(
            densityFunction, DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.NOODLE), 1.0, 1.0), -60, 320, -1
        );
        DensityFunction densityFunction3 = yLimitedInterpolatable(
            densityFunction, DensityFunctions.mappedNoise(noiseParametersLookup.getOrThrow(Noises.NOODLE_THICKNESS), 1.0, 1.0, -0.05, -0.1), -60, 320, 0
        );
        double d = 2.6666666666666665;
        DensityFunction densityFunction4 = yLimitedInterpolatable(
            densityFunction,
            DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.NOODLE_RIDGE_A), 2.6666666666666665, 2.6666666666666665),
            -60,
            320,
            0
        );
        DensityFunction densityFunction5 = yLimitedInterpolatable(
            densityFunction,
            DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.NOODLE_RIDGE_B), 2.6666666666666665, 2.6666666666666665),
            -60,
            320,
            0
        );
        DensityFunction densityFunction6 = DensityFunctions.mul(
            DensityFunctions.constant(1.5), DensityFunctions.max(densityFunction4.abs(), densityFunction5.abs())
        );
        return DensityFunctions.rangeChoice(
            densityFunction2, -1000000.0, 0.0, DensityFunctions.constant(64.0), DensityFunctions.add(densityFunction3, densityFunction6)
        );
    }

    private static DensityFunction pillars(HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup) {
        double d = 25.0;
        double e = 0.3;
        DensityFunction densityFunction = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.PILLAR), 25.0, 0.3);
        DensityFunction densityFunction2 = DensityFunctions.mappedNoise(noiseParametersLookup.getOrThrow(Noises.PILLAR_RARENESS), 0.0, -2.0);
        DensityFunction densityFunction3 = DensityFunctions.mappedNoise(noiseParametersLookup.getOrThrow(Noises.PILLAR_THICKNESS), 0.0, 1.1);
        DensityFunction densityFunction4 = DensityFunctions.add(DensityFunctions.mul(densityFunction, DensityFunctions.constant(2.0)), densityFunction2);
        return DensityFunctions.cacheOnce(DensityFunctions.mul(densityFunction4, densityFunction3.cube()));
    }

    private static DensityFunction spaghetti2D(
        HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup
    ) {
        DensityFunction densityFunction = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_2D_MODULATOR), 2.0, 1.0);
        DensityFunction densityFunction2 = DensityFunctions.weirdScaledSampler(
            densityFunction, noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_2D), DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2
        );
        DensityFunction densityFunction3 = DensityFunctions.mappedNoise(
            noiseParametersLookup.getOrThrow(Noises.SPAGHETTI_2D_ELEVATION), 0.0, (double)Math.floorDiv(-64, 8), 8.0
        );
        DensityFunction densityFunction4 = getFunction(densityFunctionLookup, SPAGHETTI_2D_THICKNESS_MODULATOR);
        DensityFunction densityFunction5 = DensityFunctions.add(densityFunction3, DensityFunctions.yClampedGradient(-64, 320, 8.0, -40.0)).abs();
        DensityFunction densityFunction6 = DensityFunctions.add(densityFunction5, densityFunction4).cube();
        double d = 0.083;
        DensityFunction densityFunction7 = DensityFunctions.add(densityFunction2, DensityFunctions.mul(DensityFunctions.constant(0.083), densityFunction4));
        return DensityFunctions.max(densityFunction7, densityFunction6).clamp(-1.0, 1.0);
    }

    private static DensityFunction underground(
        HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup, DensityFunction slopedCheese
    ) {
        DensityFunction densityFunction = getFunction(densityFunctionLookup, SPAGHETTI_2D);
        DensityFunction densityFunction2 = getFunction(densityFunctionLookup, SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityFunction3 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.CAVE_LAYER), 8.0);
        DensityFunction densityFunction4 = DensityFunctions.mul(DensityFunctions.constant(4.0), densityFunction3.square());
        DensityFunction densityFunction5 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666);
        DensityFunction densityFunction6 = DensityFunctions.add(
            DensityFunctions.add(DensityFunctions.constant(0.27), densityFunction5).clamp(-1.0, 1.0),
            DensityFunctions.add(DensityFunctions.constant(1.5), DensityFunctions.mul(DensityFunctions.constant(-0.64), slopedCheese)).clamp(0.0, 0.5)
        );
        DensityFunction densityFunction7 = DensityFunctions.add(densityFunction4, densityFunction6);
        DensityFunction densityFunction8 = DensityFunctions.min(
            DensityFunctions.min(densityFunction7, getFunction(densityFunctionLookup, ENTRANCES)), DensityFunctions.add(densityFunction, densityFunction2)
        );
        DensityFunction densityFunction9 = getFunction(densityFunctionLookup, PILLARS);
        DensityFunction densityFunction10 = DensityFunctions.rangeChoice(
            densityFunction9, -1000000.0, 0.03, DensityFunctions.constant(-1000000.0), densityFunction9
        );
        return DensityFunctions.max(densityFunction8, densityFunction10);
    }

    private static DensityFunction postProcess(DensityFunction density) {
        DensityFunction densityFunction = DensityFunctions.blendDensity(density);
        return DensityFunctions.mul(DensityFunctions.interpolated(densityFunction), DensityFunctions.constant(0.64)).squeeze();
    }

    protected static NoiseRouter overworld(
        HolderGetter<DensityFunction> densityFunctionLookup,
        HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup,
        boolean largeBiomes,
        boolean amplified
    ) {
        DensityFunction densityFunction = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.AQUIFER_BARRIER), 0.5);
        DensityFunction densityFunction2 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67);
        DensityFunction densityFunction3 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143);
        DensityFunction densityFunction4 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.AQUIFER_LAVA));
        DensityFunction densityFunction5 = getFunction(densityFunctionLookup, SHIFT_X);
        DensityFunction densityFunction6 = getFunction(densityFunctionLookup, SHIFT_Z);
        DensityFunction densityFunction7 = DensityFunctions.shiftedNoise2d(
            densityFunction5, densityFunction6, 0.25, noiseParametersLookup.getOrThrow(largeBiomes ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE)
        );
        DensityFunction densityFunction8 = DensityFunctions.shiftedNoise2d(
            densityFunction5, densityFunction6, 0.25, noiseParametersLookup.getOrThrow(largeBiomes ? Noises.VEGETATION_LARGE : Noises.VEGETATION)
        );
        DensityFunction densityFunction9 = getFunction(densityFunctionLookup, largeBiomes ? FACTOR_LARGE : (amplified ? FACTOR_AMPLIFIED : FACTOR));
        DensityFunction densityFunction10 = getFunction(densityFunctionLookup, largeBiomes ? DEPTH_LARGE : (amplified ? DEPTH_AMPLIFIED : DEPTH));
        DensityFunction densityFunction11 = noiseGradientDensity(DensityFunctions.cache2d(densityFunction9), densityFunction10);
        DensityFunction densityFunction12 = getFunction(
            densityFunctionLookup, largeBiomes ? SLOPED_CHEESE_LARGE : (amplified ? SLOPED_CHEESE_AMPLIFIED : SLOPED_CHEESE)
        );
        DensityFunction densityFunction13 = DensityFunctions.min(
            densityFunction12, DensityFunctions.mul(DensityFunctions.constant(5.0), getFunction(densityFunctionLookup, ENTRANCES))
        );
        DensityFunction densityFunction14 = DensityFunctions.rangeChoice(
            densityFunction12, -1000000.0, 1.5625, densityFunction13, underground(densityFunctionLookup, noiseParametersLookup, densityFunction12)
        );
        DensityFunction densityFunction15 = DensityFunctions.min(
            postProcess(slideOverworld(amplified, densityFunction14)), getFunction(densityFunctionLookup, NOODLE)
        );
        DensityFunction densityFunction16 = getFunction(densityFunctionLookup, Y);
        int i = Stream.of(OreVeinifier.VeinType.values()).mapToInt(veinType -> veinType.minY).min().orElse(-DimensionType.MIN_Y * 2);
        int j = Stream.of(OreVeinifier.VeinType.values()).mapToInt(veinType -> veinType.maxY).max().orElse(-DimensionType.MIN_Y * 2);
        DensityFunction densityFunction17 = yLimitedInterpolatable(
            densityFunction16, DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.ORE_VEININESS), 1.5, 1.5), i, j, 0
        );
        float f = 4.0F;
        DensityFunction densityFunction18 = yLimitedInterpolatable(
                densityFunction16, DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.ORE_VEIN_A), 4.0, 4.0), i, j, 0
            )
            .abs();
        DensityFunction densityFunction19 = yLimitedInterpolatable(
                densityFunction16, DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.ORE_VEIN_B), 4.0, 4.0), i, j, 0
            )
            .abs();
        DensityFunction densityFunction20 = DensityFunctions.add(DensityFunctions.constant(-0.08F), DensityFunctions.max(densityFunction18, densityFunction19));
        DensityFunction densityFunction21 = DensityFunctions.noise(noiseParametersLookup.getOrThrow(Noises.ORE_GAP));
        return new NoiseRouter(
            densityFunction,
            densityFunction2,
            densityFunction3,
            densityFunction4,
            densityFunction7,
            densityFunction8,
            getFunction(densityFunctionLookup, largeBiomes ? CONTINENTS_LARGE : CONTINENTS),
            getFunction(densityFunctionLookup, largeBiomes ? EROSION_LARGE : EROSION),
            densityFunction10,
            getFunction(densityFunctionLookup, RIDGES),
            slideOverworld(amplified, DensityFunctions.add(densityFunction11, DensityFunctions.constant(-0.703125)).clamp(-64.0, 64.0)),
            densityFunction15,
            densityFunction17,
            densityFunction20,
            densityFunction21
        );
    }

    private static NoiseRouter noNewCaves(
        HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup, DensityFunction density
    ) {
        DensityFunction densityFunction = getFunction(densityFunctionLookup, SHIFT_X);
        DensityFunction densityFunction2 = getFunction(densityFunctionLookup, SHIFT_Z);
        DensityFunction densityFunction3 = DensityFunctions.shiftedNoise2d(
            densityFunction, densityFunction2, 0.25, noiseParametersLookup.getOrThrow(Noises.TEMPERATURE)
        );
        DensityFunction densityFunction4 = DensityFunctions.shiftedNoise2d(
            densityFunction, densityFunction2, 0.25, noiseParametersLookup.getOrThrow(Noises.VEGETATION)
        );
        DensityFunction densityFunction5 = postProcess(density);
        return new NoiseRouter(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction3,
            densityFunction4,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction5,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero()
        );
    }

    private static DensityFunction slideOverworld(boolean amplified, DensityFunction density) {
        return slide(density, -64, 384, amplified ? 16 : 80, amplified ? 0 : 64, -0.078125, 0, 24, amplified ? 0.4 : 0.1171875);
    }

    private static DensityFunction slideNetherLike(HolderGetter<DensityFunction> densityFunctionLookup, int minY, int maxY) {
        return slide(getFunction(densityFunctionLookup, BASE_3D_NOISE_NETHER), minY, maxY, 24, 0, 0.9375, -8, 24, 2.5);
    }

    private static DensityFunction slideEndLike(DensityFunction function, int minY, int maxY) {
        return slide(function, minY, maxY, 72, -184, -23.4375, 4, 32, -0.234375);
    }

    protected static NoiseRouter nether(HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup) {
        return noNewCaves(densityFunctionLookup, noiseParametersLookup, slideNetherLike(densityFunctionLookup, 0, 128));
    }

    protected static NoiseRouter caves(HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup) {
        return noNewCaves(densityFunctionLookup, noiseParametersLookup, slideNetherLike(densityFunctionLookup, -64, 192));
    }

    protected static NoiseRouter floatingIslands(
        HolderGetter<DensityFunction> densityFunctionLookup, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup
    ) {
        return noNewCaves(densityFunctionLookup, noiseParametersLookup, slideEndLike(getFunction(densityFunctionLookup, BASE_3D_NOISE_END), 0, 256));
    }

    private static DensityFunction slideEnd(DensityFunction slopedCheese) {
        return slideEndLike(slopedCheese, 0, 128);
    }

    protected static NoiseRouter end(HolderGetter<DensityFunction> densityFunctionLookup) {
        DensityFunction densityFunction = DensityFunctions.cache2d(DensityFunctions.endIslands(0L));
        DensityFunction densityFunction2 = postProcess(slideEnd(getFunction(densityFunctionLookup, SLOPED_CHEESE_END)));
        return new NoiseRouter(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            densityFunction,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            slideEnd(DensityFunctions.add(densityFunction, DensityFunctions.constant(-0.703125))),
            densityFunction2,
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero()
        );
    }

    protected static NoiseRouter none() {
        return new NoiseRouter(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero()
        );
    }

    private static DensityFunction splineWithBlending(DensityFunction function, DensityFunction blendOffset) {
        DensityFunction densityFunction = DensityFunctions.lerp(DensityFunctions.blendAlpha(), blendOffset, function);
        return DensityFunctions.flatCache(DensityFunctions.cache2d(densityFunction));
    }

    private static DensityFunction noiseGradientDensity(DensityFunction factor, DensityFunction depth) {
        DensityFunction densityFunction = DensityFunctions.mul(depth, factor);
        return DensityFunctions.mul(DensityFunctions.constant(4.0), densityFunction.quarterNegative());
    }

    private static DensityFunction yLimitedInterpolatable(
        DensityFunction y, DensityFunction whenInRange, int minInclusive, int maxInclusive, int whenOutOfRange
    ) {
        return DensityFunctions.interpolated(
            DensityFunctions.rangeChoice(y, (double)minInclusive, (double)(maxInclusive + 1), whenInRange, DensityFunctions.constant((double)whenOutOfRange))
        );
    }

    private static DensityFunction slide(
        DensityFunction density,
        int minY,
        int maxY,
        int topRelativeMinY,
        int topRelativeMaxY,
        double topDensity,
        int bottomRelativeMinY,
        int bottomRelativeMaxY,
        double bottomDensity
    ) {
        DensityFunction densityFunction2 = DensityFunctions.yClampedGradient(minY + maxY - topRelativeMinY, minY + maxY - topRelativeMaxY, 1.0, 0.0);
        DensityFunction densityFunction = DensityFunctions.lerp(densityFunction2, topDensity, density);
        DensityFunction densityFunction3 = DensityFunctions.yClampedGradient(minY + bottomRelativeMinY, minY + bottomRelativeMaxY, 0.0, 1.0);
        return DensityFunctions.lerp(densityFunction3, bottomDensity, densityFunction);
    }

    protected static final class QuantizedSpaghettiRarity {
        protected static double getSphaghettiRarity2D(double value) {
            if (value < -0.75) {
                return 0.5;
            } else if (value < -0.5) {
                return 0.75;
            } else if (value < 0.5) {
                return 1.0;
            } else {
                return value < 0.75 ? 2.0 : 3.0;
            }
        }

        protected static double getSpaghettiRarity3D(double value) {
            if (value < -0.5) {
                return 0.75;
            } else if (value < 0.0) {
                return 1.0;
            } else {
                return value < 0.5 ? 1.5 : 2.0;
            }
        }
    }
}
