package net.minecraft.world.level.levelgen;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.PrimaryLevelData;

public record WorldDimensions(Map<ResourceKey<LevelStem>, LevelStem> dimensions) {
    public static final MapCodec<WorldDimensions> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.unboundedMap(ResourceKey.codec(Registries.LEVEL_STEM), LevelStem.CODEC).fieldOf("dimensions").forGetter(WorldDimensions::dimensions)
                )
                .apply(instance, instance.stable(WorldDimensions::new))
    );
    private static final Set<ResourceKey<LevelStem>> BUILTIN_ORDER = ImmutableSet.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END);
    private static final int VANILLA_DIMENSION_COUNT = BUILTIN_ORDER.size();

    public WorldDimensions(Map<ResourceKey<LevelStem>, LevelStem> dimensions) {
        LevelStem levelStem = dimensions.get(LevelStem.OVERWORLD);
        if (levelStem == null) {
            throw new IllegalStateException("Overworld settings missing");
        } else {
            this.dimensions = dimensions;
        }
    }

    public WorldDimensions(Registry<LevelStem> dimensionOptionsRegistry) {
        this(dimensionOptionsRegistry.holders().collect(Collectors.toMap(Holder.Reference::key, Holder.Reference::value)));
    }

    public static Stream<ResourceKey<LevelStem>> keysInOrder(Stream<ResourceKey<LevelStem>> otherKeys) {
        return Stream.concat(BUILTIN_ORDER.stream(), otherKeys.filter(key -> !BUILTIN_ORDER.contains(key)));
    }

    public WorldDimensions replaceOverworldGenerator(RegistryAccess dynamicRegistryManager, ChunkGenerator chunkGenerator) {
        Registry<DimensionType> registry = dynamicRegistryManager.registryOrThrow(Registries.DIMENSION_TYPE);
        Map<ResourceKey<LevelStem>, LevelStem> map = withOverworld(registry, this.dimensions, chunkGenerator);
        return new WorldDimensions(map);
    }

    public static Map<ResourceKey<LevelStem>, LevelStem> withOverworld(
        Registry<DimensionType> dynamicRegistry, Map<ResourceKey<LevelStem>, LevelStem> dimensionOptions, ChunkGenerator chunkGenerator
    ) {
        LevelStem levelStem = dimensionOptions.get(LevelStem.OVERWORLD);
        Holder<DimensionType> holder = (Holder<DimensionType>)(levelStem == null
            ? dynamicRegistry.getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD)
            : levelStem.type());
        return withOverworld(dimensionOptions, holder, chunkGenerator);
    }

    public static Map<ResourceKey<LevelStem>, LevelStem> withOverworld(
        Map<ResourceKey<LevelStem>, LevelStem> dimensionOptions, Holder<DimensionType> overworld, ChunkGenerator chunkGenerator
    ) {
        Builder<ResourceKey<LevelStem>, LevelStem> builder = ImmutableMap.builder();
        builder.putAll(dimensionOptions);
        builder.put(LevelStem.OVERWORLD, new LevelStem(overworld, chunkGenerator));
        return builder.buildKeepingLast();
    }

    public ChunkGenerator overworld() {
        LevelStem levelStem = this.dimensions.get(LevelStem.OVERWORLD);
        if (levelStem == null) {
            throw new IllegalStateException("Overworld settings missing");
        } else {
            return levelStem.generator();
        }
    }

    public Optional<LevelStem> get(ResourceKey<LevelStem> key) {
        return Optional.ofNullable(this.dimensions.get(key));
    }

    public ImmutableSet<ResourceKey<Level>> levels() {
        return this.dimensions().keySet().stream().map(Registries::levelStemToLevel).collect(ImmutableSet.toImmutableSet());
    }

    public boolean isDebug() {
        return this.overworld() instanceof DebugLevelSource;
    }

    private static PrimaryLevelData.SpecialWorldProperty specialWorldProperty(Registry<LevelStem> dimensionOptionsRegistry) {
        return dimensionOptionsRegistry.getOptional(LevelStem.OVERWORLD).map(overworldEntry -> {
            ChunkGenerator chunkGenerator = overworldEntry.generator();
            if (chunkGenerator instanceof DebugLevelSource) {
                return PrimaryLevelData.SpecialWorldProperty.DEBUG;
            } else {
                return chunkGenerator instanceof FlatLevelSource ? PrimaryLevelData.SpecialWorldProperty.FLAT : PrimaryLevelData.SpecialWorldProperty.NONE;
            }
        }).orElse(PrimaryLevelData.SpecialWorldProperty.NONE);
    }

    static Lifecycle checkStability(ResourceKey<LevelStem> key, LevelStem dimensionOptions) {
        return isVanillaLike(key, dimensionOptions) ? Lifecycle.stable() : Lifecycle.experimental();
    }

    private static boolean isVanillaLike(ResourceKey<LevelStem> key, LevelStem dimensionOptions) {
        if (key == LevelStem.OVERWORLD) {
            return isStableOverworld(dimensionOptions);
        } else {
            return key == LevelStem.NETHER ? isStableNether(dimensionOptions) : key == LevelStem.END && isStableEnd(dimensionOptions);
        }
    }

    private static boolean isStableOverworld(LevelStem dimensionOptions) {
        Holder<DimensionType> holder = dimensionOptions.type();
        if (!holder.is(BuiltinDimensionTypes.OVERWORLD) && !holder.is(BuiltinDimensionTypes.OVERWORLD_CAVES)) {
            return false;
        } else {
            if (dimensionOptions.generator().getBiomeSource() instanceof MultiNoiseBiomeSource multiNoiseBiomeSource
                && !multiNoiseBiomeSource.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)) {
                return false;
            }

            return true;
        }
    }

    private static boolean isStableNether(LevelStem dimensionOptions) {
        return dimensionOptions.type().is(BuiltinDimensionTypes.NETHER)
            && dimensionOptions.generator() instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator
            && noiseBasedChunkGenerator.stable(NoiseGeneratorSettings.NETHER)
            && noiseBasedChunkGenerator.getBiomeSource() instanceof MultiNoiseBiomeSource multiNoiseBiomeSource
            && multiNoiseBiomeSource.stable(MultiNoiseBiomeSourceParameterLists.NETHER);
    }

    private static boolean isStableEnd(LevelStem dimensionOptions) {
        return dimensionOptions.type().is(BuiltinDimensionTypes.END)
            && dimensionOptions.generator() instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator
            && noiseBasedChunkGenerator.stable(NoiseGeneratorSettings.END)
            && noiseBasedChunkGenerator.getBiomeSource() instanceof TheEndBiomeSource;
    }

    public WorldDimensions.Complete bake(Registry<LevelStem> existingRegistry) {
        Stream<ResourceKey<LevelStem>> stream = Stream.concat(existingRegistry.registryKeySet().stream(), this.dimensions.keySet().stream()).distinct();

        record Entry(ResourceKey<LevelStem> key, LevelStem value) {
            RegistrationInfo registrationInfo() {
                return new RegistrationInfo(Optional.empty(), WorldDimensions.checkStability(this.key, this.value));
            }
        }

        List<Entry> list = new ArrayList<>();
        keysInOrder(stream)
            .forEach(
                key -> existingRegistry.getOptional((ResourceKey<LevelStem>)key)
                        .or(() -> Optional.ofNullable(this.dimensions.get(key)))
                        .ifPresent(dimensionOptions -> list.add(new Entry(key, dimensionOptions)))
            );
        Lifecycle lifecycle = list.size() == VANILLA_DIMENSION_COUNT ? Lifecycle.stable() : Lifecycle.experimental();
        WritableRegistry<LevelStem> writableRegistry = new MappedRegistry<>(Registries.LEVEL_STEM, lifecycle);
        list.forEach(entry -> writableRegistry.register(entry.key, entry.value, entry.registrationInfo()));
        Registry<LevelStem> registry = writableRegistry.freeze();
        PrimaryLevelData.SpecialWorldProperty specialWorldProperty = specialWorldProperty(registry);
        return new WorldDimensions.Complete(registry.freeze(), specialWorldProperty);
    }

    public static record Complete(Registry<LevelStem> dimensions, PrimaryLevelData.SpecialWorldProperty specialWorldProperty) {
        public Lifecycle lifecycle() {
            return this.dimensions.registryLifecycle();
        }

        public RegistryAccess.Frozen dimensionsRegistryAccess() {
            return new RegistryAccess.ImmutableRegistryAccess(List.of(this.dimensions)).freeze();
        }
    }
}
