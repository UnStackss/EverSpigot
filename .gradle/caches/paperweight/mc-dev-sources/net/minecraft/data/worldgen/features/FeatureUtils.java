package net.minecraft.data.worldgen.features;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class FeatureUtils {
    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> featureRegisterable) {
        AquaticFeatures.bootstrap(featureRegisterable);
        CaveFeatures.bootstrap(featureRegisterable);
        EndFeatures.bootstrap(featureRegisterable);
        MiscOverworldFeatures.bootstrap(featureRegisterable);
        NetherFeatures.bootstrap(featureRegisterable);
        OreFeatures.bootstrap(featureRegisterable);
        PileFeatures.bootstrap(featureRegisterable);
        TreeFeatures.bootstrap(featureRegisterable);
        VegetationFeatures.bootstrap(featureRegisterable);
    }

    private static BlockPredicate simplePatchPredicate(List<Block> validGround) {
        BlockPredicate blockPredicate;
        if (!validGround.isEmpty()) {
            blockPredicate = BlockPredicate.allOf(BlockPredicate.ONLY_IN_AIR_PREDICATE, BlockPredicate.matchesBlocks(Direction.DOWN.getNormal(), validGround));
        } else {
            blockPredicate = BlockPredicate.ONLY_IN_AIR_PREDICATE;
        }

        return blockPredicate;
    }

    public static RandomPatchConfiguration simpleRandomPatchConfiguration(int tries, Holder<PlacedFeature> feature) {
        return new RandomPatchConfiguration(tries, 7, 3, feature);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> RandomPatchConfiguration simplePatchConfiguration(
        F feature, FC config, List<Block> predicateBlocks, int tries
    ) {
        return simpleRandomPatchConfiguration(tries, PlacementUtils.filtered(feature, config, simplePatchPredicate(predicateBlocks)));
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> RandomPatchConfiguration simplePatchConfiguration(
        F feature, FC config, List<Block> predicateBlocks
    ) {
        return simplePatchConfiguration(feature, config, predicateBlocks, 96);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> RandomPatchConfiguration simplePatchConfiguration(F feature, FC config) {
        return simplePatchConfiguration(feature, config, List.of(), 96);
    }

    public static ResourceKey<ConfiguredFeature<?, ?>> createKey(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(id));
    }

    public static void register(
        BootstrapContext<ConfiguredFeature<?, ?>> registerable, ResourceKey<ConfiguredFeature<?, ?>> key, Feature<NoneFeatureConfiguration> feature
    ) {
        register(registerable, key, feature, FeatureConfiguration.NONE);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(
        BootstrapContext<ConfiguredFeature<?, ?>> registerable, ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC config
    ) {
        registerable.register(key, new ConfiguredFeature(feature, config));
    }
}
