package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class UniformFloat extends FloatProvider {
    public static final MapCodec<UniformFloat> CODEC = RecordCodecBuilder.<UniformFloat>mapCodec(
            instance -> instance.group(
                        Codec.FLOAT.fieldOf("min_inclusive").forGetter(provider -> provider.minInclusive),
                        Codec.FLOAT.fieldOf("max_exclusive").forGetter(provider -> provider.maxExclusive)
                    )
                    .apply(instance, UniformFloat::new)
        )
        .validate(
            provider -> provider.maxExclusive <= provider.minInclusive
                    ? DataResult.error(
                        () -> "Max must be larger than min, min_inclusive: " + provider.minInclusive + ", max_exclusive: " + provider.maxExclusive
                    )
                    : DataResult.success(provider)
        );
    private final float minInclusive;
    private final float maxExclusive;

    private UniformFloat(float min, float max) {
        this.minInclusive = min;
        this.maxExclusive = max;
    }

    public static UniformFloat of(float min, float max) {
        if (max <= min) {
            throw new IllegalArgumentException("Max must exceed min");
        } else {
            return new UniformFloat(min, max);
        }
    }

    @Override
    public float sample(RandomSource random) {
        return Mth.randomBetween(random, this.minInclusive, this.maxExclusive);
    }

    @Override
    public float getMinValue() {
        return this.minInclusive;
    }

    @Override
    public float getMaxValue() {
        return this.maxExclusive;
    }

    @Override
    public FloatProviderType<?> getType() {
        return FloatProviderType.UNIFORM;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxExclusive + "]";
    }
}
