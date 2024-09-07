package net.minecraft.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.mutable.MutableObject;

public interface CubicSpline<C, I extends ToFloatFunction<C>> extends ToFloatFunction<C> {
    @VisibleForDebug
    String parityString();

    CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor);

    static <C, I extends ToFloatFunction<C>> Codec<CubicSpline<C, I>> codec(Codec<I> locationFunctionCodec) {
        MutableObject<Codec<CubicSpline<C, I>>> mutableObject = new MutableObject<>();

        record Point<C, I extends ToFloatFunction<C>>(float location, CubicSpline<C, I> value, float derivative) {
        }

        Codec<Point<C, I>> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                        Codec.FLOAT.fieldOf("location").forGetter(Point::location),
                        Codec.lazyInitialized(mutableObject::getValue).fieldOf("value").forGetter(Point::value),
                        Codec.FLOAT.fieldOf("derivative").forGetter(Point::derivative)
                    )
                    .apply(instance, (location, value, derivative) -> new Point<>(location, value, derivative))
        );
        Codec<CubicSpline.Multipoint<C, I>> codec2 = RecordCodecBuilder.create(
            instance -> instance.group(
                        locationFunctionCodec.fieldOf("coordinate").forGetter(CubicSpline.Multipoint::coordinate),
                        ExtraCodecs.nonEmptyList(codec.listOf())
                            .fieldOf("points")
                            .forGetter(
                                spline -> IntStream.range(0, spline.locations.length)
                                        .mapToObj(index -> new Point<>(spline.locations()[index], spline.values().get(index), spline.derivatives()[index]))
                                        .toList()
                            )
                    )
                    .apply(instance, (locationFunction, splines) -> {
                        float[] fs = new float[splines.size()];
                        ImmutableList.Builder<CubicSpline<C, I>> builder = ImmutableList.builder();
                        float[] gs = new float[splines.size()];

                        for (int i = 0; i < splines.size(); i++) {
                            Point<C, I> lv = splines.get(i);
                            fs[i] = lv.location();
                            builder.add(lv.value());
                            gs[i] = lv.derivative();
                        }

                        return CubicSpline.Multipoint.create(locationFunction, fs, builder.build(), gs);
                    })
        );
        mutableObject.setValue(
            Codec.either(Codec.FLOAT, codec2)
                .xmap(
                    either -> either.map(CubicSpline.Constant::new, spline -> spline),
                    spline -> spline instanceof CubicSpline.Constant<C, I> constant
                            ? Either.left(constant.value())
                            : Either.right((CubicSpline.Multipoint<C, I>)spline)
                )
        );
        return mutableObject.getValue();
    }

    static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> constant(float value) {
        return new CubicSpline.Constant<>(value);
    }

    static <C, I extends ToFloatFunction<C>> CubicSpline.Builder<C, I> builder(I locationFunction) {
        return new CubicSpline.Builder<>(locationFunction);
    }

    static <C, I extends ToFloatFunction<C>> CubicSpline.Builder<C, I> builder(I locationFunction, ToFloatFunction<Float> amplifier) {
        return new CubicSpline.Builder<>(locationFunction, amplifier);
    }

    public static final class Builder<C, I extends ToFloatFunction<C>> {
        private final I coordinate;
        private final ToFloatFunction<Float> valueTransformer;
        private final FloatList locations = new FloatArrayList();
        private final List<CubicSpline<C, I>> values = Lists.newArrayList();
        private final FloatList derivatives = new FloatArrayList();

        protected Builder(I locationFunction) {
            this(locationFunction, ToFloatFunction.IDENTITY);
        }

        protected Builder(I locationFunction, ToFloatFunction<Float> amplifier) {
            this.coordinate = locationFunction;
            this.valueTransformer = amplifier;
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value) {
            return this.addPoint(location, new CubicSpline.Constant<>(this.valueTransformer.apply(value)), 0.0F);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, float value, float derivative) {
            return this.addPoint(location, new CubicSpline.Constant<>(this.valueTransformer.apply(value)), derivative);
        }

        public CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> value) {
            return this.addPoint(location, value, 0.0F);
        }

        private CubicSpline.Builder<C, I> addPoint(float location, CubicSpline<C, I> value, float derivative) {
            if (!this.locations.isEmpty() && location <= this.locations.getFloat(this.locations.size() - 1)) {
                throw new IllegalArgumentException("Please register points in ascending order");
            } else {
                this.locations.add(location);
                this.values.add(value);
                this.derivatives.add(derivative);
                return this;
            }
        }

        public CubicSpline<C, I> build() {
            if (this.locations.isEmpty()) {
                throw new IllegalStateException("No elements added");
            } else {
                return CubicSpline.Multipoint.create(
                    this.coordinate, this.locations.toFloatArray(), ImmutableList.copyOf(this.values), this.derivatives.toFloatArray()
                );
            }
        }
    }

    @VisibleForDebug
    public static record Constant<C, I extends ToFloatFunction<C>>(float value) implements CubicSpline<C, I> {
        @Override
        public float apply(C x) {
            return this.value;
        }

        @Override
        public String parityString() {
            return String.format(Locale.ROOT, "k=%.3f", this.value);
        }

        @Override
        public float minValue() {
            return this.value;
        }

        @Override
        public float maxValue() {
            return this.value;
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return this;
        }
    }

    public interface CoordinateVisitor<I> {
        I visit(I value);
    }

    @VisibleForDebug
    public static record Multipoint<C, I extends ToFloatFunction<C>>(
        I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, @Override float minValue, @Override float maxValue
    ) implements CubicSpline<C, I> {
        public Multipoint(I coordinate, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives, float minValue, float maxValue) {
            validateSizes(locations, values, derivatives);
            this.coordinate = coordinate;
            this.locations = locations;
            this.values = values;
            this.derivatives = derivatives;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        static <C, I extends ToFloatFunction<C>> CubicSpline.Multipoint<C, I> create(
            I locationFunction, float[] locations, List<CubicSpline<C, I>> values, float[] derivatives
        ) {
            validateSizes(locations, values, derivatives);
            int i = locations.length - 1;
            float f = Float.POSITIVE_INFINITY;
            float g = Float.NEGATIVE_INFINITY;
            float h = locationFunction.minValue();
            float j = locationFunction.maxValue();
            if (h < locations[0]) {
                float k = linearExtend(h, locations, values.get(0).minValue(), derivatives, 0);
                float l = linearExtend(h, locations, values.get(0).maxValue(), derivatives, 0);
                f = Math.min(f, Math.min(k, l));
                g = Math.max(g, Math.max(k, l));
            }

            if (j > locations[i]) {
                float m = linearExtend(j, locations, values.get(i).minValue(), derivatives, i);
                float n = linearExtend(j, locations, values.get(i).maxValue(), derivatives, i);
                f = Math.min(f, Math.min(m, n));
                g = Math.max(g, Math.max(m, n));
            }

            for (CubicSpline<C, I> cubicSpline : values) {
                f = Math.min(f, cubicSpline.minValue());
                g = Math.max(g, cubicSpline.maxValue());
            }

            for (int o = 0; o < i; o++) {
                float p = locations[o];
                float q = locations[o + 1];
                float r = q - p;
                CubicSpline<C, I> cubicSpline2 = values.get(o);
                CubicSpline<C, I> cubicSpline3 = values.get(o + 1);
                float s = cubicSpline2.minValue();
                float t = cubicSpline2.maxValue();
                float u = cubicSpline3.minValue();
                float v = cubicSpline3.maxValue();
                float w = derivatives[o];
                float x = derivatives[o + 1];
                if (w != 0.0F || x != 0.0F) {
                    float y = w * r;
                    float z = x * r;
                    float aa = Math.min(s, u);
                    float ab = Math.max(t, v);
                    float ac = y - v + s;
                    float ad = y - u + t;
                    float ae = -z + u - t;
                    float af = -z + v - s;
                    float ag = Math.min(ac, ae);
                    float ah = Math.max(ad, af);
                    f = Math.min(f, aa + 0.25F * ag);
                    g = Math.max(g, ab + 0.25F * ah);
                }
            }

            return new CubicSpline.Multipoint<>(locationFunction, locations, values, derivatives, f, g);
        }

        private static float linearExtend(float point, float[] locations, float value, float[] derivatives, int i) {
            float f = derivatives[i];
            return f == 0.0F ? value : value + f * (point - locations[i]);
        }

        private static <C, I extends ToFloatFunction<C>> void validateSizes(float[] locations, List<CubicSpline<C, I>> values, float[] derivatives) {
            if (locations.length != values.size() || locations.length != derivatives.length) {
                throw new IllegalArgumentException("All lengths must be equal, got: " + locations.length + " " + values.size() + " " + derivatives.length);
            } else if (locations.length == 0) {
                throw new IllegalArgumentException("Cannot create a multipoint spline with no points");
            }
        }

        @Override
        public float apply(C x) {
            float f = this.coordinate.apply(x);
            int i = findIntervalStart(this.locations, f);
            int j = this.locations.length - 1;
            if (i < 0) {
                return linearExtend(f, this.locations, this.values.get(0).apply(x), this.derivatives, 0);
            } else if (i == j) {
                return linearExtend(f, this.locations, this.values.get(j).apply(x), this.derivatives, j);
            } else {
                float g = this.locations[i];
                float h = this.locations[i + 1];
                float k = (f - g) / (h - g);
                ToFloatFunction<C> toFloatFunction = (ToFloatFunction<C>)this.values.get(i);
                ToFloatFunction<C> toFloatFunction2 = (ToFloatFunction<C>)this.values.get(i + 1);
                float l = this.derivatives[i];
                float m = this.derivatives[i + 1];
                float n = toFloatFunction.apply(x);
                float o = toFloatFunction2.apply(x);
                float p = l * (h - g) - (o - n);
                float q = -m * (h - g) + (o - n);
                return Mth.lerp(k, n, o) + k * (1.0F - k) * Mth.lerp(k, p, q);
            }
        }

        private static int findIntervalStart(float[] locations, float x) {
            return Mth.binarySearch(0, locations.length, i -> x < locations[i]) - 1;
        }

        @VisibleForTesting
        @Override
        public String parityString() {
            return "Spline{coordinate="
                + this.coordinate
                + ", locations="
                + this.toString(this.locations)
                + ", derivatives="
                + this.toString(this.derivatives)
                + ", values="
                + this.values.stream().map(CubicSpline::parityString).collect(Collectors.joining(", ", "[", "]"))
                + "}";
        }

        private String toString(float[] values) {
            return "["
                + IntStream.range(0, values.length)
                    .mapToDouble(index -> (double)values[index])
                    .mapToObj(value -> String.format(Locale.ROOT, "%.3f", value))
                    .collect(Collectors.joining(", "))
                + "]";
        }

        @Override
        public CubicSpline<C, I> mapAll(CubicSpline.CoordinateVisitor<I> visitor) {
            return create(visitor.visit(this.coordinate), this.locations, this.values().stream().map(value -> value.mapAll(visitor)).toList(), this.derivatives);
        }
    }
}
