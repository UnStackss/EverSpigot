package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

public class BiomeGenerationSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BiomeGenerationSettings EMPTY = new BiomeGenerationSettings(ImmutableMap.of(), ImmutableList.of());
    public static final MapCodec<BiomeGenerationSettings> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.simpleMap(
                            GenerationStep.Carving.CODEC,
                            ConfiguredWorldCarver.LIST_CODEC.promotePartial(Util.prefix("Carver: ", LOGGER::error)),
                            StringRepresentable.keys(GenerationStep.Carving.values())
                        )
                        .fieldOf("carvers")
                        .forGetter(generationSettings -> generationSettings.carvers),
                    PlacedFeature.LIST_OF_LISTS_CODEC
                        .promotePartial(Util.prefix("Features: ", LOGGER::error))
                        .fieldOf("features")
                        .forGetter(generationSettings -> generationSettings.features)
                )
                .apply(instance, BiomeGenerationSettings::new)
    );
    private final Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> carvers;
    private final List<HolderSet<PlacedFeature>> features;
    private final Supplier<List<ConfiguredFeature<?, ?>>> flowerFeatures;
    private final Supplier<Set<PlacedFeature>> featureSet;

    BiomeGenerationSettings(Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> carvers, List<HolderSet<PlacedFeature>> features) {
        this.carvers = carvers;
        this.features = features;
        this.flowerFeatures = Suppliers.memoize(
            () -> features.stream()
                    .flatMap(HolderSet::stream)
                    .map(Holder::value)
                    .flatMap(PlacedFeature::getFeatures)
                    .filter(feature -> feature.feature() == Feature.FLOWER)
                    .collect(ImmutableList.toImmutableList())
        );
        this.featureSet = Suppliers.memoize(() -> features.stream().flatMap(HolderSet::stream).map(Holder::value).collect(Collectors.toSet()));
    }

    public Iterable<Holder<ConfiguredWorldCarver<?>>> getCarvers(GenerationStep.Carving carverStep) {
        return Objects.requireNonNullElseGet(this.carvers.get(carverStep), List::of);
    }

    public List<ConfiguredFeature<?, ?>> getFlowerFeatures() {
        return this.flowerFeatures.get();
    }

    public List<HolderSet<PlacedFeature>> features() {
        return this.features;
    }

    public boolean hasFeature(PlacedFeature feature) {
        return this.featureSet.get().contains(feature);
    }

    public static class Builder extends BiomeGenerationSettings.PlainBuilder {
        private final HolderGetter<PlacedFeature> placedFeatures;
        private final HolderGetter<ConfiguredWorldCarver<?>> worldCarvers;

        public Builder(HolderGetter<PlacedFeature> placedFeatureLookup, HolderGetter<ConfiguredWorldCarver<?>> configuredCarverLookup) {
            this.placedFeatures = placedFeatureLookup;
            this.worldCarvers = configuredCarverLookup;
        }

        public BiomeGenerationSettings.Builder addFeature(GenerationStep.Decoration featureStep, ResourceKey<PlacedFeature> featureKey) {
            this.addFeature(featureStep.ordinal(), this.placedFeatures.getOrThrow(featureKey));
            return this;
        }

        public BiomeGenerationSettings.Builder addCarver(GenerationStep.Carving carverStep, ResourceKey<ConfiguredWorldCarver<?>> carverKey) {
            this.addCarver(carverStep, this.worldCarvers.getOrThrow(carverKey));
            return this;
        }
    }

    public static class PlainBuilder {
        private final Map<GenerationStep.Carving, List<Holder<ConfiguredWorldCarver<?>>>> carvers = Maps.newLinkedHashMap();
        private final List<List<Holder<PlacedFeature>>> features = Lists.newArrayList();

        public BiomeGenerationSettings.PlainBuilder addFeature(GenerationStep.Decoration featureStep, Holder<PlacedFeature> featureEntry) {
            return this.addFeature(featureStep.ordinal(), featureEntry);
        }

        public BiomeGenerationSettings.PlainBuilder addFeature(int ordinal, Holder<PlacedFeature> featureEntry) {
            this.addFeatureStepsUpTo(ordinal);
            this.features.get(ordinal).add(featureEntry);
            return this;
        }

        public BiomeGenerationSettings.PlainBuilder addCarver(GenerationStep.Carving carverStep, Holder<ConfiguredWorldCarver<?>> carverEntry) {
            this.carvers.computeIfAbsent(carverStep, step -> Lists.newArrayList()).add(carverEntry);
            return this;
        }

        private void addFeatureStepsUpTo(int size) {
            while (this.features.size() <= size) {
                this.features.add(Lists.newArrayList());
            }
        }

        public BiomeGenerationSettings build() {
            return new BiomeGenerationSettings(
                this.carvers.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, entry -> HolderSet.direct(entry.getValue()))),
                this.features.stream().map(HolderSet::direct).collect(ImmutableList.toImmutableList())
            );
        }
    }
}
