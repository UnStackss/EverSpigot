package net.minecraft.world.level.levelgen.presets;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public class WorldPresets {
    public static final ResourceKey<WorldPreset> NORMAL = register("normal");
    public static final ResourceKey<WorldPreset> FLAT = register("flat");
    public static final ResourceKey<WorldPreset> LARGE_BIOMES = register("large_biomes");
    public static final ResourceKey<WorldPreset> AMPLIFIED = register("amplified");
    public static final ResourceKey<WorldPreset> SINGLE_BIOME_SURFACE = register("single_biome_surface");
    public static final ResourceKey<WorldPreset> DEBUG = register("debug_all_block_states");

    public static void bootstrap(BootstrapContext<WorldPreset> presetRegisterable) {
        new WorldPresets.Bootstrap(presetRegisterable).bootstrap();
    }

    private static ResourceKey<WorldPreset> register(String id) {
        return ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.withDefaultNamespace(id));
    }

    public static Optional<ResourceKey<WorldPreset>> fromSettings(WorldDimensions registry) {
        return registry.get(LevelStem.OVERWORLD).flatMap(overworld -> {
            Object var10000;
            Objects.requireNonNull(var10000);
            ChunkGenerator chunkGenerator = (ChunkGenerator)var10000;

            overworld.generator();
            return switch (chunkGenerator) {
                case FlatLevelSource flatLevelSource -> Optional.of(FLAT);
                case DebugLevelSource debugLevelSource -> Optional.of(DEBUG);
                case NoiseBasedChunkGenerator noiseBasedChunkGenerator -> Optional.of(NORMAL);
                default -> Optional.empty();
            };
        });
    }

    public static WorldDimensions createNormalWorldDimensions(RegistryAccess dynamicRegistryManager) {
        return dynamicRegistryManager.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(NORMAL).value().createWorldDimensions();
    }

    public static LevelStem getNormalOverworld(RegistryAccess dynamicRegistryManager) {
        return dynamicRegistryManager.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(NORMAL).value().overworld().orElseThrow();
    }

    static class Bootstrap {
        private final BootstrapContext<WorldPreset> context;
        private final HolderGetter<NoiseGeneratorSettings> noiseSettings;
        private final HolderGetter<Biome> biomes;
        private final HolderGetter<PlacedFeature> placedFeatures;
        private final HolderGetter<StructureSet> structureSets;
        private final HolderGetter<MultiNoiseBiomeSourceParameterList> multiNoiseBiomeSourceParameterLists;
        private final Holder<DimensionType> overworldDimensionType;
        private final LevelStem netherStem;
        private final LevelStem endStem;

        Bootstrap(BootstrapContext<WorldPreset> presetRegisterable) {
            this.context = presetRegisterable;
            HolderGetter<DimensionType> holderGetter = presetRegisterable.lookup(Registries.DIMENSION_TYPE);
            this.noiseSettings = presetRegisterable.lookup(Registries.NOISE_SETTINGS);
            this.biomes = presetRegisterable.lookup(Registries.BIOME);
            this.placedFeatures = presetRegisterable.lookup(Registries.PLACED_FEATURE);
            this.structureSets = presetRegisterable.lookup(Registries.STRUCTURE_SET);
            this.multiNoiseBiomeSourceParameterLists = presetRegisterable.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            this.overworldDimensionType = holderGetter.getOrThrow(BuiltinDimensionTypes.OVERWORLD);
            Holder<DimensionType> holder = holderGetter.getOrThrow(BuiltinDimensionTypes.NETHER);
            Holder<NoiseGeneratorSettings> holder2 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.NETHER);
            Holder.Reference<MultiNoiseBiomeSourceParameterList> reference = this.multiNoiseBiomeSourceParameterLists
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
            this.netherStem = new LevelStem(holder, new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(reference), holder2));
            Holder<DimensionType> holder3 = holderGetter.getOrThrow(BuiltinDimensionTypes.END);
            Holder<NoiseGeneratorSettings> holder4 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.END);
            this.endStem = new LevelStem(holder3, new NoiseBasedChunkGenerator(TheEndBiomeSource.create(this.biomes), holder4));
        }

        private LevelStem makeOverworld(ChunkGenerator chunkGenerator) {
            return new LevelStem(this.overworldDimensionType, chunkGenerator);
        }

        private LevelStem makeNoiseBasedOverworld(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> chunkGeneratorSettings) {
            return this.makeOverworld(new NoiseBasedChunkGenerator(biomeSource, chunkGeneratorSettings));
        }

        private WorldPreset createPresetWithCustomOverworld(LevelStem dimensionOptions) {
            return new WorldPreset(Map.of(LevelStem.OVERWORLD, dimensionOptions, LevelStem.NETHER, this.netherStem, LevelStem.END, this.endStem));
        }

        private void registerCustomOverworldPreset(ResourceKey<WorldPreset> key, LevelStem dimensionOptions) {
            this.context.register(key, this.createPresetWithCustomOverworld(dimensionOptions));
        }

        private void registerOverworlds(BiomeSource biomeSource) {
            Holder<NoiseGeneratorSettings> holder = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            this.registerCustomOverworldPreset(WorldPresets.NORMAL, this.makeNoiseBasedOverworld(biomeSource, holder));
            Holder<NoiseGeneratorSettings> holder2 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.LARGE_BIOMES);
            this.registerCustomOverworldPreset(WorldPresets.LARGE_BIOMES, this.makeNoiseBasedOverworld(biomeSource, holder2));
            Holder<NoiseGeneratorSettings> holder3 = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.AMPLIFIED);
            this.registerCustomOverworldPreset(WorldPresets.AMPLIFIED, this.makeNoiseBasedOverworld(biomeSource, holder3));
        }

        public void bootstrap() {
            Holder.Reference<MultiNoiseBiomeSourceParameterList> reference = this.multiNoiseBiomeSourceParameterLists
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            this.registerOverworlds(MultiNoiseBiomeSource.createFromPreset(reference));
            Holder<NoiseGeneratorSettings> holder = this.noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            Holder.Reference<Biome> reference2 = this.biomes.getOrThrow(Biomes.PLAINS);
            this.registerCustomOverworldPreset(WorldPresets.SINGLE_BIOME_SURFACE, this.makeNoiseBasedOverworld(new FixedBiomeSource(reference2), holder));
            this.registerCustomOverworldPreset(
                WorldPresets.FLAT,
                this.makeOverworld(new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(this.biomes, this.structureSets, this.placedFeatures)))
            );
            this.registerCustomOverworldPreset(WorldPresets.DEBUG, this.makeOverworld(new DebugLevelSource(reference2)));
        }
    }
}
