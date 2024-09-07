package net.minecraft.world.flag;

import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

public class FeatureFlags {
    public static final FeatureFlag VANILLA;
    public static final FeatureFlag BUNDLE;
    public static final FeatureFlag TRADE_REBALANCE;
    public static final FeatureFlagRegistry REGISTRY;
    public static final Codec<FeatureFlagSet> CODEC;
    public static final FeatureFlagSet VANILLA_SET;
    public static final FeatureFlagSet DEFAULT_FLAGS;

    public static String printMissingFlags(FeatureFlagSet featuresToCheck, FeatureFlagSet features) {
        return printMissingFlags(REGISTRY, featuresToCheck, features);
    }

    public static String printMissingFlags(FeatureFlagRegistry featureManager, FeatureFlagSet featuresToCheck, FeatureFlagSet features) {
        Set<ResourceLocation> set = featureManager.toNames(features);
        Set<ResourceLocation> set2 = featureManager.toNames(featuresToCheck);
        return set.stream().filter(id -> !set2.contains(id)).map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }

    public static boolean isExperimental(FeatureFlagSet features) {
        return !features.isSubsetOf(VANILLA_SET);
    }

    static {
        FeatureFlagRegistry.Builder builder = new FeatureFlagRegistry.Builder("main");
        VANILLA = builder.createVanilla("vanilla");
        BUNDLE = builder.createVanilla("bundle");
        TRADE_REBALANCE = builder.createVanilla("trade_rebalance");
        REGISTRY = builder.build();
        CODEC = REGISTRY.codec();
        VANILLA_SET = FeatureFlagSet.of(VANILLA);
        DEFAULT_FLAGS = VANILLA_SET;
    }
}
