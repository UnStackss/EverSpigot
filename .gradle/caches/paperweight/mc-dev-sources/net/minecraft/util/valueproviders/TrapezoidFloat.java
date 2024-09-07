package net.minecraft.util.valueproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

public class TrapezoidFloat extends FloatProvider {
    public static final MapCodec<TrapezoidFloat> CODEC = RecordCodecBuilder.<TrapezoidFloat>mapCodec(
            instance -> instance.group(
                        Codec.FLOAT.fieldOf("min").forGetter(provider -> provider.min),
                        Codec.FLOAT.fieldOf("max").forGetter(provider -> provider.max),
                        Codec.FLOAT.fieldOf("plateau").forGetter(provider -> provider.plateau)
                    )
                    .apply(instance, TrapezoidFloat::new)
        )
        .validate(
            provider -> {
                if (provider.max < provider.min) {
                    return DataResult.error(() -> "Max must be larger than min: [" + provider.min + ", " + provider.max + "]");
                } else {
                    return provider.plateau > provider.max - provider.min
                        ? DataResult.error(() -> "Plateau can at most be the full span: [" + provider.min + ", " + provider.max + "]")
                        : DataResult.success(provider);
                }
            }
        );
    private final float min;
    private final float max;
    private final float plateau;

    public static TrapezoidFloat of(float min, float max, float plateau) {
        return new TrapezoidFloat(min, max, plateau);
    }

    private TrapezoidFloat(float min, float max, float plateau) {
        this.min = min;
        this.max = max;
        this.plateau = plateau;
    }

    @Override
    public float sample(RandomSource random) {
        float f = this.max - this.min;
        float g = (f - this.plateau) / 2.0F;
        float h = f - g;
        return this.min + random.nextFloat() * h + random.nextFloat() * g;
    }

    @Override
    public float getMinValue() {
        return this.min;
    }

    @Override
    public float getMaxValue() {
        return this.max;
    }

    @Override
    public FloatProviderType<?> getType() {
        return FloatProviderType.TRAPEZOID;
    }

    @Override
    public String toString() {
        return "trapezoid(" + this.plateau + ") in [" + this.min + "-" + this.max + "]";
    }
}
