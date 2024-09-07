package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;

public interface StringRepresentable {
    int PRE_BUILT_MAP_THRESHOLD = 16;

    String getSerializedName();

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnum(Supplier<E[]> enumValues) {
        return fromEnumWithMapping(enumValues, id -> id);
    }

    static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnumWithMapping(
        Supplier<E[]> enumValues, Function<String, String> valueNameTransformer
    ) {
        E[] enums = (E[])enumValues.get();
        Function<String, E> function = createNameLookup(enums, valueNameTransformer);
        return new StringRepresentable.EnumCodec<>(enums, function);
    }

    static <T extends StringRepresentable> Codec<T> fromValues(Supplier<T[]> values) {
        T[] stringRepresentables = (T[])values.get();
        Function<String, T> function = createNameLookup(stringRepresentables, valueName -> valueName);
        ToIntFunction<T> toIntFunction = Util.createIndexLookup(Arrays.asList(stringRepresentables));
        return new StringRepresentable.StringRepresentableCodec<>(stringRepresentables, function, toIntFunction);
    }

    static <T extends StringRepresentable> Function<String, T> createNameLookup(T[] values, Function<String, String> valueNameTransformer) {
        if (values.length > 16) {
            Map<String, T> map = Arrays.<StringRepresentable>stream(values)
                .collect(Collectors.toMap(value -> valueNameTransformer.apply(value.getSerializedName()), value -> (T)value));
            return name -> name == null ? null : map.get(name);
        } else {
            return name -> {
                for (T stringRepresentable : values) {
                    if (valueNameTransformer.apply(stringRepresentable.getSerializedName()).equals(name)) {
                        return stringRepresentable;
                    }
                }

                return null;
            };
        }
    }

    static Keyable keys(StringRepresentable[] values) {
        return new Keyable() {
            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return Arrays.stream(values).map(StringRepresentable::getSerializedName).map(dynamicOps::createString);
            }
        };
    }

    @Deprecated
    public static class EnumCodec<E extends Enum<E> & StringRepresentable> extends StringRepresentable.StringRepresentableCodec<E> {
        private final Function<String, E> resolver;

        public EnumCodec(E[] values, Function<String, E> idToIdentifiable) {
            super(values, idToIdentifiable, enum_ -> enum_.ordinal());
            this.resolver = idToIdentifiable;
        }

        @Nullable
        public E byName(@Nullable String id) {
            return this.resolver.apply(id);
        }

        public E byName(@Nullable String id, E fallback) {
            return Objects.requireNonNullElse(this.byName(id), fallback);
        }
    }

    public static class StringRepresentableCodec<S extends StringRepresentable> implements Codec<S> {
        private final Codec<S> codec;

        public StringRepresentableCodec(S[] values, Function<String, S> idToIdentifiable, ToIntFunction<S> identifiableToOrdinal) {
            this.codec = ExtraCodecs.orCompressed(
                Codec.stringResolver(StringRepresentable::getSerializedName, idToIdentifiable),
                ExtraCodecs.idResolverCodec(identifiableToOrdinal, ordinal -> ordinal >= 0 && ordinal < values.length ? values[ordinal] : null, -1)
            );
        }

        public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> dynamicOps, T object) {
            return this.codec.decode(dynamicOps, object);
        }

        public <T> DataResult<T> encode(S stringRepresentable, DynamicOps<T> dynamicOps, T object) {
            return this.codec.encode(stringRepresentable, dynamicOps, object);
        }
    }
}
