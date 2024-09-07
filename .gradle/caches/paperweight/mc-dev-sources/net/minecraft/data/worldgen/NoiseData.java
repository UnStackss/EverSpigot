package net.minecraft.data.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseData {
    @Deprecated
    public static final NormalNoise.NoiseParameters DEFAULT_SHIFT = new NormalNoise.NoiseParameters(-3, 1.0, 1.0, 1.0, 0.0);

    public static void bootstrap(BootstrapContext<NormalNoise.NoiseParameters> noiseParametersRegisterable) {
        registerBiomeNoises(noiseParametersRegisterable, 0, Noises.TEMPERATURE, Noises.VEGETATION, Noises.CONTINENTALNESS, Noises.EROSION);
        registerBiomeNoises(
            noiseParametersRegisterable, -2, Noises.TEMPERATURE_LARGE, Noises.VEGETATION_LARGE, Noises.CONTINENTALNESS_LARGE, Noises.EROSION_LARGE
        );
        register(noiseParametersRegisterable, Noises.RIDGE, -7, 1.0, 2.0, 1.0, 0.0, 0.0, 0.0);
        noiseParametersRegisterable.register(Noises.SHIFT, DEFAULT_SHIFT);
        register(noiseParametersRegisterable, Noises.AQUIFER_BARRIER, -3, 1.0);
        register(noiseParametersRegisterable, Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS, -7, 1.0);
        register(noiseParametersRegisterable, Noises.AQUIFER_LAVA, -1, 1.0);
        register(noiseParametersRegisterable, Noises.AQUIFER_FLUID_LEVEL_SPREAD, -5, 1.0);
        register(noiseParametersRegisterable, Noises.PILLAR, -7, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.PILLAR_RARENESS, -8, 1.0);
        register(noiseParametersRegisterable, Noises.PILLAR_THICKNESS, -8, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_2D, -7, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_2D_ELEVATION, -8, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_2D_MODULATOR, -11, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_2D_THICKNESS, -11, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_3D_1, -7, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_3D_2, -7, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_3D_RARITY, -11, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_3D_THICKNESS, -8, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_ROUGHNESS, -5, 1.0);
        register(noiseParametersRegisterable, Noises.SPAGHETTI_ROUGHNESS_MODULATOR, -8, 1.0);
        register(noiseParametersRegisterable, Noises.CAVE_ENTRANCE, -7, 0.4, 0.5, 1.0);
        register(noiseParametersRegisterable, Noises.CAVE_LAYER, -8, 1.0);
        register(noiseParametersRegisterable, Noises.CAVE_CHEESE, -8, 0.5, 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 2.0, 0.0);
        register(noiseParametersRegisterable, Noises.ORE_VEININESS, -8, 1.0);
        register(noiseParametersRegisterable, Noises.ORE_VEIN_A, -7, 1.0);
        register(noiseParametersRegisterable, Noises.ORE_VEIN_B, -7, 1.0);
        register(noiseParametersRegisterable, Noises.ORE_GAP, -5, 1.0);
        register(noiseParametersRegisterable, Noises.NOODLE, -8, 1.0);
        register(noiseParametersRegisterable, Noises.NOODLE_THICKNESS, -8, 1.0);
        register(noiseParametersRegisterable, Noises.NOODLE_RIDGE_A, -7, 1.0);
        register(noiseParametersRegisterable, Noises.NOODLE_RIDGE_B, -7, 1.0);
        register(noiseParametersRegisterable, Noises.JAGGED, -16, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.SURFACE, -6, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.SURFACE_SECONDARY, -6, 1.0, 1.0, 0.0, 1.0);
        register(noiseParametersRegisterable, Noises.CLAY_BANDS_OFFSET, -8, 1.0);
        register(noiseParametersRegisterable, Noises.BADLANDS_PILLAR, -2, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.BADLANDS_PILLAR_ROOF, -8, 1.0);
        register(noiseParametersRegisterable, Noises.BADLANDS_SURFACE, -6, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.ICEBERG_PILLAR, -6, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.ICEBERG_PILLAR_ROOF, -3, 1.0);
        register(noiseParametersRegisterable, Noises.ICEBERG_SURFACE, -6, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.SWAMP, -2, 1.0);
        register(noiseParametersRegisterable, Noises.CALCITE, -9, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.GRAVEL, -8, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.POWDER_SNOW, -6, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.PACKED_ICE, -7, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.ICE, -4, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, Noises.SOUL_SAND_LAYER, -8, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.013333333333333334);
        register(noiseParametersRegisterable, Noises.GRAVEL_LAYER, -8, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.013333333333333334);
        register(noiseParametersRegisterable, Noises.PATCH, -5, 1.0, 0.0, 0.0, 0.0, 0.0, 0.013333333333333334);
        register(noiseParametersRegisterable, Noises.NETHERRACK, -3, 1.0, 0.0, 0.0, 0.35);
        register(noiseParametersRegisterable, Noises.NETHER_WART, -3, 1.0, 0.0, 0.0, 0.9);
        register(noiseParametersRegisterable, Noises.NETHER_STATE_SELECTOR, -4, 1.0);
    }

    private static void registerBiomeNoises(
        BootstrapContext<NormalNoise.NoiseParameters> noiseParametersRegisterable,
        int octaveOffset,
        ResourceKey<NormalNoise.NoiseParameters> temperatureKey,
        ResourceKey<NormalNoise.NoiseParameters> vegetationKey,
        ResourceKey<NormalNoise.NoiseParameters> continentalnessKey,
        ResourceKey<NormalNoise.NoiseParameters> erosionKey
    ) {
        register(noiseParametersRegisterable, temperatureKey, -10 + octaveOffset, 1.5, 0.0, 1.0, 0.0, 0.0, 0.0);
        register(noiseParametersRegisterable, vegetationKey, -8 + octaveOffset, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0);
        register(noiseParametersRegisterable, continentalnessKey, -9 + octaveOffset, 1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0);
        register(noiseParametersRegisterable, erosionKey, -9 + octaveOffset, 1.0, 1.0, 0.0, 1.0, 1.0);
    }

    private static void register(
        BootstrapContext<NormalNoise.NoiseParameters> noiseParametersRegisterable,
        ResourceKey<NormalNoise.NoiseParameters> key,
        int firstOctave,
        double firstAmplitude,
        double... amplitudes
    ) {
        noiseParametersRegisterable.register(key, new NormalNoise.NoiseParameters(firstOctave, firstAmplitude, amplitudes));
    }
}
