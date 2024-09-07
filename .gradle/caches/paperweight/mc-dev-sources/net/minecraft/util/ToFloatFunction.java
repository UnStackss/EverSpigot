package net.minecraft.util;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.function.Function;

public interface ToFloatFunction<C> {
    ToFloatFunction<Float> IDENTITY = createUnlimited(value -> value);

    float apply(C x);

    float minValue();

    float maxValue();

    static ToFloatFunction<Float> createUnlimited(Float2FloatFunction delegate) {
        return new ToFloatFunction<Float>() {
            @Override
            public float apply(Float x) {
                return delegate.apply(x);
            }

            @Override
            public float minValue() {
                return Float.NEGATIVE_INFINITY;
            }

            @Override
            public float maxValue() {
                return Float.POSITIVE_INFINITY;
            }
        };
    }

    default <C2> ToFloatFunction<C2> comap(Function<C2, C> before) {
        final ToFloatFunction<C> toFloatFunction = this;
        return new ToFloatFunction<C2>() {
            @Override
            public float apply(C2 x) {
                return toFloatFunction.apply(before.apply(x));
            }

            @Override
            public float minValue() {
                return toFloatFunction.minValue();
            }

            @Override
            public float maxValue() {
                return toFloatFunction.maxValue();
            }
        };
    }
}
