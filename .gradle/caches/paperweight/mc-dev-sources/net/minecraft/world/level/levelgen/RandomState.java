package net.minecraft.world.level.levelgen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class RandomState {
    final PositionalRandomFactory random;
    private final HolderGetter<NormalNoise.NoiseParameters> noises;
    private final NoiseRouter router;
    private final Climate.Sampler sampler;
    private final SurfaceSystem surfaceSystem;
    private final PositionalRandomFactory aquiferRandom;
    private final PositionalRandomFactory oreRandom;
    private final Map<ResourceKey<NormalNoise.NoiseParameters>, NormalNoise> noiseIntances;
    private final Map<ResourceLocation, PositionalRandomFactory> positionalRandoms;

    public static RandomState create(HolderGetter.Provider registryLookup, ResourceKey<NoiseGeneratorSettings> chunkGeneratorSettingsKey, long legacyWorldSeed) {
        return create(
            registryLookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(chunkGeneratorSettingsKey).value(),
            registryLookup.lookupOrThrow(Registries.NOISE),
            legacyWorldSeed
        );
    }

    public static RandomState create(
        NoiseGeneratorSettings chunkGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup, long legacyWorldSeed
    ) {
        return new RandomState(chunkGeneratorSettings, noiseParametersLookup, legacyWorldSeed);
    }

    private RandomState(NoiseGeneratorSettings chunkGeneratorSettings, HolderGetter<NormalNoise.NoiseParameters> noiseParametersLookup, long seed) {
        this.random = chunkGeneratorSettings.getRandomSource().newInstance(seed).forkPositional();
        this.noises = noiseParametersLookup;
        this.aquiferRandom = this.random.fromHashOf(ResourceLocation.withDefaultNamespace("aquifer")).forkPositional();
        this.oreRandom = this.random.fromHashOf(ResourceLocation.withDefaultNamespace("ore")).forkPositional();
        this.noiseIntances = new ConcurrentHashMap<>();
        this.positionalRandoms = new ConcurrentHashMap<>();
        this.surfaceSystem = new SurfaceSystem(this, chunkGeneratorSettings.defaultBlock(), chunkGeneratorSettings.seaLevel(), this.random);
        final boolean bl = chunkGeneratorSettings.useLegacyRandomSource();

        class NoiseWiringHelper implements DensityFunction.Visitor {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private RandomSource newLegacyInstance(long seed) {
                return new LegacyRandomSource(seed + seed);
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseDensityFunction) {
                Holder<NormalNoise.NoiseParameters> holder = noiseDensityFunction.noiseData();
                if (bl) {
                    if (holder.is(Noises.TEMPERATURE)) {
                        NormalNoise normalNoise = NormalNoise.createLegacyNetherBiome(this.newLegacyInstance(0L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0));
                        return new DensityFunction.NoiseHolder(holder, normalNoise);
                    }

                    if (holder.is(Noises.VEGETATION)) {
                        NormalNoise normalNoise2 = NormalNoise.createLegacyNetherBiome(
                            this.newLegacyInstance(1L), new NormalNoise.NoiseParameters(-7, 1.0, 1.0)
                        );
                        return new DensityFunction.NoiseHolder(holder, normalNoise2);
                    }

                    if (holder.is(Noises.SHIFT)) {
                        NormalNoise normalNoise3 = NormalNoise.create(
                            RandomState.this.random.fromHashOf(Noises.SHIFT.location()), new NormalNoise.NoiseParameters(0, 0.0)
                        );
                        return new DensityFunction.NoiseHolder(holder, normalNoise3);
                    }
                }

                NormalNoise normalNoise4 = RandomState.this.getOrCreateNoise(holder.unwrapKey().orElseThrow());
                return new DensityFunction.NoiseHolder(holder, normalNoise4);
            }

            private DensityFunction wrapNew(DensityFunction densityFunction) {
                if (densityFunction instanceof BlendedNoise blendedNoise) {
                    RandomSource randomSource = bl
                        ? this.newLegacyInstance(0L)
                        : RandomState.this.random.fromHashOf(ResourceLocation.withDefaultNamespace("terrain"));
                    return blendedNoise.withNewRandom(randomSource);
                } else {
                    return (DensityFunction)(densityFunction instanceof DensityFunctions.EndIslandDensityFunction
                        ? new DensityFunctions.EndIslandDensityFunction(seed)
                        : densityFunction);
                }
            }

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
            }
        }

        this.router = chunkGeneratorSettings.noiseRouter().mapAll(new NoiseWiringHelper());
        DensityFunction.Visitor visitor = new DensityFunction.Visitor() {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            private DensityFunction wrapNew(DensityFunction densityFunction) {
                if (densityFunction instanceof DensityFunctions.HolderHolder holderHolder) {
                    return holderHolder.function().value();
                } else {
                    return densityFunction instanceof DensityFunctions.Marker marker ? marker.wrapped() : densityFunction;
                }
            }

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return this.wrapped.computeIfAbsent(densityFunction, this::wrapNew);
            }
        };
        this.sampler = new Climate.Sampler(
            this.router.temperature().mapAll(visitor),
            this.router.vegetation().mapAll(visitor),
            this.router.continents().mapAll(visitor),
            this.router.erosion().mapAll(visitor),
            this.router.depth().mapAll(visitor),
            this.router.ridges().mapAll(visitor),
            chunkGeneratorSettings.spawnTarget()
        );
    }

    public NormalNoise getOrCreateNoise(ResourceKey<NormalNoise.NoiseParameters> noiseParametersKey) {
        return this.noiseIntances.computeIfAbsent(noiseParametersKey, key -> Noises.instantiate(this.noises, this.random, noiseParametersKey));
    }

    public PositionalRandomFactory getOrCreateRandomFactory(ResourceLocation id) {
        return this.positionalRandoms.computeIfAbsent(id, id2 -> this.random.fromHashOf(id).forkPositional());
    }

    public NoiseRouter router() {
        return this.router;
    }

    public Climate.Sampler sampler() {
        return this.sampler;
    }

    public SurfaceSystem surfaceSystem() {
        return this.surfaceSystem;
    }

    public PositionalRandomFactory aquiferRandom() {
        return this.aquiferRandom;
    }

    public PositionalRandomFactory oreRandom() {
        return this.oreRandom;
    }
}
