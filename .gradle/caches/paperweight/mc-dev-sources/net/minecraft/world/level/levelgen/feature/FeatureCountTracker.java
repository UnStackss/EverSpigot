package net.minecraft.world.level.levelgen.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class FeatureCountTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LoadingCache<ServerLevel, FeatureCountTracker.LevelData> data = CacheBuilder.newBuilder()
        .weakKeys()
        .expireAfterAccess(5L, TimeUnit.MINUTES)
        .build(new CacheLoader<ServerLevel, FeatureCountTracker.LevelData>() {
            @Override
            public FeatureCountTracker.LevelData load(ServerLevel serverLevel) {
                return new FeatureCountTracker.LevelData(Object2IntMaps.synchronize(new Object2IntOpenHashMap<>()), new MutableInt(0));
            }
        });

    public static void chunkDecorated(ServerLevel world) {
        try {
            data.get(world).chunksWithFeatures().increment();
        } catch (Exception var2) {
            LOGGER.error("Failed to increment chunk count", (Throwable)var2);
        }
    }

    public static void featurePlaced(ServerLevel world, ConfiguredFeature<?, ?> configuredFeature, Optional<PlacedFeature> placedFeature) {
        try {
            data.get(world)
                .featureData()
                .computeInt(new FeatureCountTracker.FeatureData(configuredFeature, placedFeature), (featureData, count) -> count == null ? 1 : count + 1);
        } catch (Exception var4) {
            LOGGER.error("Failed to increment feature count", (Throwable)var4);
        }
    }

    public static void clearCounts() {
        data.invalidateAll();
        LOGGER.debug("Cleared feature counts");
    }

    public static void logCounts() {
        LOGGER.debug("Logging feature counts:");
        data.asMap()
            .forEach(
                (world, features) -> {
                    String string = world.dimension().location().toString();
                    boolean bl = world.getServer().isRunning();
                    Registry<PlacedFeature> registry = world.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
                    String string2 = (bl ? "running" : "dead") + " " + string;
                    Integer integer = features.chunksWithFeatures().getValue();
                    LOGGER.debug(string2 + " total_chunks: " + integer);
                    features.featureData()
                        .forEach(
                            (featureData, count) -> LOGGER.debug(
                                    string2
                                        + " "
                                        + String.format(Locale.ROOT, "%10d ", count)
                                        + String.format(Locale.ROOT, "%10f ", (double)count.intValue() / (double)integer.intValue())
                                        + featureData.topFeature().flatMap(registry::getResourceKey).<ResourceLocation>map(ResourceKey::location)
                                        + " "
                                        + featureData.feature().feature()
                                        + " "
                                        + featureData.feature()
                                )
                        );
                }
            );
    }

    static record FeatureData(ConfiguredFeature<?, ?> feature, Optional<PlacedFeature> topFeature) {
    }

    static record LevelData(Object2IntMap<FeatureCountTracker.FeatureData> featureData, MutableInt chunksWithFeatures) {
    }
}
