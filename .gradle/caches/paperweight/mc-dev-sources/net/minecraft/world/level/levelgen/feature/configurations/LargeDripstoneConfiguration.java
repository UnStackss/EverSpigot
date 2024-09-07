package net.minecraft.world.level.levelgen.feature.configurations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;

public class LargeDripstoneConfiguration implements FeatureConfiguration {
    public static final Codec<LargeDripstoneConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Codec.intRange(1, 512).fieldOf("floor_to_ceiling_search_range").orElse(30).forGetter(config -> config.floorToCeilingSearchRange),
                    IntProvider.codec(1, 60).fieldOf("column_radius").forGetter(config -> config.columnRadius),
                    FloatProvider.codec(0.0F, 20.0F).fieldOf("height_scale").forGetter(config -> config.heightScale),
                    Codec.floatRange(0.1F, 1.0F).fieldOf("max_column_radius_to_cave_height_ratio").forGetter(config -> config.maxColumnRadiusToCaveHeightRatio),
                    FloatProvider.codec(0.1F, 10.0F).fieldOf("stalactite_bluntness").forGetter(config -> config.stalactiteBluntness),
                    FloatProvider.codec(0.1F, 10.0F).fieldOf("stalagmite_bluntness").forGetter(config -> config.stalagmiteBluntness),
                    FloatProvider.codec(0.0F, 2.0F).fieldOf("wind_speed").forGetter(config -> config.windSpeed),
                    Codec.intRange(0, 100).fieldOf("min_radius_for_wind").forGetter(config -> config.minRadiusForWind),
                    Codec.floatRange(0.0F, 5.0F).fieldOf("min_bluntness_for_wind").forGetter(config -> config.minBluntnessForWind)
                )
                .apply(instance, LargeDripstoneConfiguration::new)
    );
    public final int floorToCeilingSearchRange;
    public final IntProvider columnRadius;
    public final FloatProvider heightScale;
    public final float maxColumnRadiusToCaveHeightRatio;
    public final FloatProvider stalactiteBluntness;
    public final FloatProvider stalagmiteBluntness;
    public final FloatProvider windSpeed;
    public final int minRadiusForWind;
    public final float minBluntnessForWind;

    public LargeDripstoneConfiguration(
        int floorToCeilingSearchRange,
        IntProvider columnRadius,
        FloatProvider heightScale,
        float maxColumnRadiusToCaveHeightRatio,
        FloatProvider stalactiteBluntness,
        FloatProvider stalagmiteBluntness,
        FloatProvider windSpeed,
        int minRadiusForWind,
        float minBluntnessForWind
    ) {
        this.floorToCeilingSearchRange = floorToCeilingSearchRange;
        this.columnRadius = columnRadius;
        this.heightScale = heightScale;
        this.maxColumnRadiusToCaveHeightRatio = maxColumnRadiusToCaveHeightRatio;
        this.stalactiteBluntness = stalactiteBluntness;
        this.stalagmiteBluntness = stalagmiteBluntness;
        this.windSpeed = windSpeed;
        this.minRadiusForWind = minRadiusForWind;
        this.minBluntnessForWind = minBluntnessForWind;
    }
}
