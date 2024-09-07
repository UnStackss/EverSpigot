package net.minecraft.util.valueproviders;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;

public abstract class FloatProvider implements SampledFloat {
    private static final Codec<Either<Float, FloatProvider>> CONSTANT_OR_DISPATCH_CODEC = Codec.either(
        Codec.FLOAT, BuiltInRegistries.FLOAT_PROVIDER_TYPE.byNameCodec().dispatch(FloatProvider::getType, FloatProviderType::codec)
    );
    public static final Codec<FloatProvider> CODEC = CONSTANT_OR_DISPATCH_CODEC.xmap(
        either -> either.map(ConstantFloat::of, provider -> (FloatProvider)provider),
        provider -> provider.getType() == FloatProviderType.CONSTANT ? Either.left(((ConstantFloat)provider).getValue()) : Either.right(provider)
    );

    public static Codec<FloatProvider> codec(float min, float max) {
        return CODEC.validate(
            provider -> {
                if (provider.getMinValue() < min) {
                    return DataResult.error(() -> "Value provider too low: " + min + " [" + provider.getMinValue() + "-" + provider.getMaxValue() + "]");
                } else {
                    return provider.getMaxValue() > max
                        ? DataResult.error(() -> "Value provider too high: " + max + " [" + provider.getMinValue() + "-" + provider.getMaxValue() + "]")
                        : DataResult.success(provider);
                }
            }
        );
    }

    public abstract float getMinValue();

    public abstract float getMaxValue();

    public abstract FloatProviderType<?> getType();
}
