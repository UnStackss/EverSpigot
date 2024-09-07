package net.minecraft.data.worldgen.placement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public class PlacementUtils {
    public static final PlacementModifier HEIGHTMAP = HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING);
    public static final PlacementModifier HEIGHTMAP_TOP_SOLID = HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG);
    public static final PlacementModifier HEIGHTMAP_WORLD_SURFACE = HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG);
    public static final PlacementModifier HEIGHTMAP_OCEAN_FLOOR = HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR);
    public static final PlacementModifier FULL_RANGE = HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.top());
    public static final PlacementModifier RANGE_10_10 = HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(10), VerticalAnchor.belowTop(10));
    public static final PlacementModifier RANGE_8_8 = HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(8), VerticalAnchor.belowTop(8));
    public static final PlacementModifier RANGE_4_4 = HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(4), VerticalAnchor.belowTop(4));
    public static final PlacementModifier RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT = HeightRangePlacement.uniform(
        VerticalAnchor.bottom(), VerticalAnchor.absolute(256)
    );

    public static void bootstrap(BootstrapContext<PlacedFeature> featureRegisterable) {
        AquaticPlacements.bootstrap(featureRegisterable);
        CavePlacements.bootstrap(featureRegisterable);
        EndPlacements.bootstrap(featureRegisterable);
        MiscOverworldPlacements.bootstrap(featureRegisterable);
        NetherPlacements.bootstrap(featureRegisterable);
        OrePlacements.bootstrap(featureRegisterable);
        TreePlacements.bootstrap(featureRegisterable);
        VegetationPlacements.bootstrap(featureRegisterable);
        VillagePlacements.bootstrap(featureRegisterable);
    }

    public static ResourceKey<PlacedFeature> createKey(String id) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.withDefaultNamespace(id));
    }

    public static void register(
        BootstrapContext<PlacedFeature> featureRegisterable,
        ResourceKey<PlacedFeature> key,
        Holder<ConfiguredFeature<?, ?>> feature,
        List<PlacementModifier> modifiers
    ) {
        featureRegisterable.register(key, new PlacedFeature(feature, List.copyOf(modifiers)));
    }

    public static void register(
        BootstrapContext<PlacedFeature> featureRegisterable,
        ResourceKey<PlacedFeature> key,
        Holder<ConfiguredFeature<?, ?>> feature,
        PlacementModifier... modifiers
    ) {
        register(featureRegisterable, key, feature, List.of(modifiers));
    }

    public static PlacementModifier countExtra(int count, float extraChance, int extraCount) {
        float f = 1.0F / extraChance;
        if (Math.abs(f - (float)((int)f)) > 1.0E-5F) {
            throw new IllegalStateException("Chance data cannot be represented as list weight");
        } else {
            SimpleWeightedRandomList<IntProvider> simpleWeightedRandomList = SimpleWeightedRandomList.<IntProvider>builder()
                .add(ConstantInt.of(count), (int)f - 1)
                .add(ConstantInt.of(count + extraCount), 1)
                .build();
            return CountPlacement.of(new WeightedListInt(simpleWeightedRandomList));
        }
    }

    public static PlacementFilter isEmpty() {
        return BlockPredicateFilter.forPredicate(BlockPredicate.ONLY_IN_AIR_PREDICATE);
    }

    public static BlockPredicateFilter filteredByBlockSurvival(Block block) {
        return BlockPredicateFilter.forPredicate(BlockPredicate.wouldSurvive(block.defaultBlockState(), BlockPos.ZERO));
    }

    public static Holder<PlacedFeature> inlinePlaced(Holder<ConfiguredFeature<?, ?>> feature, PlacementModifier... modifiers) {
        return Holder.direct(new PlacedFeature(feature, List.of(modifiers)));
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> Holder<PlacedFeature> inlinePlaced(
        F feature, FC featureConfig, PlacementModifier... modifiers
    ) {
        return inlinePlaced(Holder.direct(new ConfiguredFeature(feature, featureConfig)), modifiers);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> Holder<PlacedFeature> onlyWhenEmpty(F feature, FC featureConfig) {
        return filtered(feature, featureConfig, BlockPredicate.ONLY_IN_AIR_PREDICATE);
    }

    public static <FC extends FeatureConfiguration, F extends Feature<FC>> Holder<PlacedFeature> filtered(F feature, FC featureConfig, BlockPredicate predicate) {
        return inlinePlaced(feature, featureConfig, BlockPredicateFilter.forPredicate(predicate));
    }
}
