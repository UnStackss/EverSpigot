package net.minecraft.world.flag;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class FeatureFlagRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final FeatureFlagUniverse universe;
    public final Map<ResourceLocation, FeatureFlag> names;
    private final FeatureFlagSet allFlags;

    FeatureFlagRegistry(FeatureFlagUniverse universe, FeatureFlagSet featureSet, Map<ResourceLocation, FeatureFlag> featureFlags) {
        this.universe = universe;
        this.names = featureFlags;
        this.allFlags = featureSet;
    }

    public boolean isSubset(FeatureFlagSet features) {
        return features.isSubsetOf(this.allFlags);
    }

    public FeatureFlagSet allFlags() {
        return this.allFlags;
    }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> features) {
        return this.fromNames(features, feature -> LOGGER.warn("Unknown feature flag: {}", feature));
    }

    public FeatureFlagSet subset(FeatureFlag... features) {
        return FeatureFlagSet.create(this.universe, Arrays.asList(features));
    }

    public FeatureFlagSet fromNames(Iterable<ResourceLocation> features, Consumer<ResourceLocation> unknownFlagConsumer) {
        Set<FeatureFlag> set = Sets.newIdentityHashSet();

        for (ResourceLocation resourceLocation : features) {
            FeatureFlag featureFlag = this.names.get(resourceLocation);
            if (featureFlag == null) {
                unknownFlagConsumer.accept(resourceLocation);
            } else {
                set.add(featureFlag);
            }
        }

        return FeatureFlagSet.create(this.universe, set);
    }

    public Set<ResourceLocation> toNames(FeatureFlagSet features) {
        Set<ResourceLocation> set = new HashSet<>();
        this.names.forEach((identifier, featureFlag) -> {
            if (features.contains(featureFlag)) {
                set.add(identifier);
            }
        });
        return set;
    }

    public Codec<FeatureFlagSet> codec() {
        return ResourceLocation.CODEC.listOf().comapFlatMap(featureIds -> {
            Set<ResourceLocation> set = new HashSet<>();
            FeatureFlagSet featureFlagSet = this.fromNames(featureIds, set::add);
            return !set.isEmpty() ? DataResult.error(() -> "Unknown feature ids: " + set, featureFlagSet) : DataResult.success(featureFlagSet);
        }, features -> List.copyOf(this.toNames(features)));
    }

    public static class Builder {
        private final FeatureFlagUniverse universe;
        private int id;
        private final Map<ResourceLocation, FeatureFlag> flags = new LinkedHashMap<>();

        public Builder(String universe) {
            this.universe = new FeatureFlagUniverse(universe);
        }

        public FeatureFlag createVanilla(String feature) {
            return this.create(ResourceLocation.withDefaultNamespace(feature));
        }

        public FeatureFlag create(ResourceLocation feature) {
            if (this.id >= 64) {
                throw new IllegalStateException("Too many feature flags");
            } else {
                FeatureFlag featureFlag = new FeatureFlag(this.universe, this.id++);
                FeatureFlag featureFlag2 = this.flags.put(feature, featureFlag);
                if (featureFlag2 != null) {
                    throw new IllegalStateException("Duplicate feature flag " + feature);
                } else {
                    return featureFlag;
                }
            }
        }

        public FeatureFlagRegistry build() {
            FeatureFlagSet featureFlagSet = FeatureFlagSet.create(this.universe, this.flags.values());
            return new FeatureFlagRegistry(this.universe, featureFlagSet, Map.copyOf(this.flags));
        }
    }
}
