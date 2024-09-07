package net.minecraft.world.level.levelgen.feature.featuresize;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;

public abstract class FeatureSize {
    public static final Codec<FeatureSize> CODEC = BuiltInRegistries.FEATURE_SIZE_TYPE.byNameCodec().dispatch(FeatureSize::type, FeatureSizeType::codec);
    protected static final int MAX_WIDTH = 16;
    protected final OptionalInt minClippedHeight;

    protected static <S extends FeatureSize> RecordCodecBuilder<S, OptionalInt> minClippedHeightCodec() {
        return Codec.intRange(0, 80)
            .optionalFieldOf("min_clipped_height")
            .xmap(
                minClippedHeight -> minClippedHeight.map(OptionalInt::of).orElse(OptionalInt.empty()),
                minClippedHeight -> minClippedHeight.isPresent() ? Optional.of(minClippedHeight.getAsInt()) : Optional.empty()
            )
            .forGetter(featureSize -> featureSize.minClippedHeight);
    }

    public FeatureSize(OptionalInt minClippedHeight) {
        this.minClippedHeight = minClippedHeight;
    }

    protected abstract FeatureSizeType<?> type();

    public abstract int getSizeAtHeight(int height, int y);

    public OptionalInt minClippedHeight() {
        return this.minClippedHeight;
    }
}
