package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

public class BiasedToBottomInt extends IntProvider {
    public static final MapCodec<BiasedToBottomInt> CODEC = RecordCodecBuilder.<BiasedToBottomInt>mapCodec(
            instance -> instance.group(
                        Codec.INT.fieldOf("min_inclusive").forGetter(provider -> provider.minInclusive),
                        Codec.INT.fieldOf("max_inclusive").forGetter(provider -> provider.maxInclusive)
                    )
                    .apply(instance, BiasedToBottomInt::new)
        )
        .validate(
            provider -> provider.maxInclusive < provider.minInclusive
                    ? DataResult.error(() -> "Max must be at least min, min_inclusive: " + provider.minInclusive + ", max_inclusive: " + provider.maxInclusive)
                    : DataResult.success(provider)
        );
    private final int minInclusive;
    private final int maxInclusive;

    private BiasedToBottomInt(int min, int max) {
        this.minInclusive = min;
        this.maxInclusive = max;
    }

    public static BiasedToBottomInt of(int min, int max) {
        return new BiasedToBottomInt(min, max);
    }

    @Override
    public int sample(RandomSource random) {
        return this.minInclusive + random.nextInt(random.nextInt(this.maxInclusive - this.minInclusive + 1) + 1);
    }

    @Override
    public int getMinValue() {
        return this.minInclusive;
    }

    @Override
    public int getMaxValue() {
        return this.maxInclusive;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.BIASED_TO_BOTTOM;
    }

    @Override
    public String toString() {
        return "[" + this.minInclusive + "-" + this.maxInclusive + "]";
    }
}
