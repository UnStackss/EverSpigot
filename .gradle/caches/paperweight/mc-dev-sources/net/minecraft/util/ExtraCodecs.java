package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.primitives.UnsignedBytes;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.Codec.ResultFunction;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ExtraCodecs {
    public static final Codec<JsonElement> JSON = converter(JsonOps.INSTANCE);
    public static final Codec<Object> JAVA = converter(JavaOps.INSTANCE);
    public static final Codec<Vector3f> VECTOR3F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 3).map(listx -> new Vector3f(listx.get(0), listx.get(1), listx.get(2))),
            vec3f -> List.of(vec3f.x(), vec3f.y(), vec3f.z())
        );
    public static final Codec<Vector4f> VECTOR4F = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 4).map(listx -> new Vector4f(listx.get(0), listx.get(1), listx.get(2), listx.get(3))),
            vec4f -> List.of(vec4f.x(), vec4f.y(), vec4f.z(), vec4f.w())
        );
    public static final Codec<Quaternionf> QUATERNIONF_COMPONENTS = Codec.FLOAT
        .listOf()
        .comapFlatMap(
            list -> Util.fixedSize((List<Float>)list, 4)
                    .map(listx -> new Quaternionf((Float)listx.get(0), (Float)listx.get(1), (Float)listx.get(2), (Float)listx.get(3)).normalize()),
            quaternion -> List.of(quaternion.x, quaternion.y, quaternion.z, quaternion.w)
        );
    public static final Codec<AxisAngle4f> AXISANGLE4F = RecordCodecBuilder.create(
        instance -> instance.group(
                    Codec.FLOAT.fieldOf("angle").forGetter(axisAngle -> axisAngle.angle),
                    VECTOR3F.fieldOf("axis").forGetter(axisAngle -> new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z))
                )
                .apply(instance, AxisAngle4f::new)
    );
    public static final Codec<Quaternionf> QUATERNIONF = Codec.withAlternative(QUATERNIONF_COMPONENTS, AXISANGLE4F.xmap(Quaternionf::new, AxisAngle4f::new));
    public static Codec<Matrix4f> MATRIX4F = Codec.FLOAT.listOf().comapFlatMap(list -> Util.fixedSize((List<Float>)list, 16).map(listx -> {
            Matrix4f matrix4f = new Matrix4f();

            for (int i = 0; i < listx.size(); i++) {
                matrix4f.setRowColumn(i >> 2, i & 3, listx.get(i));
            }

            return matrix4f.determineProperties();
        }), matrix4f -> {
        FloatList floatList = new FloatArrayList(16);

        for (int i = 0; i < 16; i++) {
            floatList.add(matrix4f.getRowColumn(i >> 2, i & 3));
        }

        return floatList;
    });
    public static final Codec<Integer> ARGB_COLOR_CODEC = Codec.withAlternative(
        Codec.INT, VECTOR4F, vec4f -> FastColor.ARGB32.colorFromFloat(vec4f.w(), vec4f.x(), vec4f.y(), vec4f.z())
    );
    public static final Codec<Integer> UNSIGNED_BYTE = Codec.BYTE
        .flatComapMap(
            UnsignedBytes::toInt,
            value -> value > 255 ? DataResult.error(() -> "Unsigned byte was too large: " + value + " > 255") : DataResult.success(value.byteValue())
        );
    public static final Codec<Integer> NON_NEGATIVE_INT = intRangeWithMessage(0, Integer.MAX_VALUE, v -> "Value must be non-negative: " + v);
    public static final Codec<Integer> POSITIVE_INT = intRangeWithMessage(1, Integer.MAX_VALUE, v -> "Value must be positive: " + v);
    public static final Codec<Float> POSITIVE_FLOAT = floatRangeMinExclusiveWithMessage(0.0F, Float.MAX_VALUE, v -> "Value must be positive: " + v);
    public static final Codec<Pattern> PATTERN = Codec.STRING.comapFlatMap(pattern -> {
        try {
            return DataResult.success(Pattern.compile(pattern));
        } catch (PatternSyntaxException var2) {
            return DataResult.error(() -> "Invalid regex pattern '" + pattern + "': " + var2.getMessage());
        }
    }, Pattern::pattern);
    public static final Codec<Instant> INSTANT_ISO8601 = temporalCodec(DateTimeFormatter.ISO_INSTANT).xmap(Instant::from, Function.identity());
    public static final Codec<byte[]> BASE64_STRING = Codec.STRING.comapFlatMap(encoded -> {
        try {
            return DataResult.success(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException var2) {
            return DataResult.error(() -> "Malformed base64 string");
        }
    }, data -> Base64.getEncoder().encodeToString(data));
    public static final Codec<String> ESCAPED_STRING = Codec.STRING
        .comapFlatMap(string -> DataResult.success(StringEscapeUtils.unescapeJava(string)), StringEscapeUtils::escapeJava);
    public static final Codec<ExtraCodecs.TagOrElementLocation> TAG_OR_ELEMENT_ID = Codec.STRING
        .comapFlatMap(
            tagEntry -> tagEntry.startsWith("#")
                    ? ResourceLocation.read(tagEntry.substring(1)).map(id -> new ExtraCodecs.TagOrElementLocation(id, true))
                    : ResourceLocation.read(tagEntry).map(id -> new ExtraCodecs.TagOrElementLocation(id, false)),
            ExtraCodecs.TagOrElementLocation::decoratedId
        );
    public static final Function<Optional<Long>, OptionalLong> toOptionalLong = optional -> optional.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    public static final Function<OptionalLong, Optional<Long>> fromOptionalLong = optionalLong -> optionalLong.isPresent()
            ? Optional.of(optionalLong.getAsLong())
            : Optional.empty();
    public static final Codec<BitSet> BIT_SET = Codec.LONG_STREAM.xmap(stream -> BitSet.valueOf(stream.toArray()), set -> Arrays.stream(set.toLongArray()));
    private static final Codec<Property> PROPERTY = RecordCodecBuilder.create(
        instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Property::name),
                    Codec.STRING.fieldOf("value").forGetter(Property::value),
                    Codec.STRING.lenientOptionalFieldOf("signature").forGetter(property -> Optional.ofNullable(property.signature()))
                )
                .apply(instance, (key, value, signature) -> new Property(key, value, signature.orElse(null)))
    );
    public static final Codec<PropertyMap> PROPERTY_MAP = Codec.either(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()), PROPERTY.listOf())
        .xmap(either -> {
            PropertyMap propertyMap = new PropertyMap();
            either.ifLeft(map -> map.forEach((key, values) -> {
                    for (String string : values) {
                        propertyMap.put(key, new Property(key, string));
                    }
                })).ifRight(properties -> {
                for (Property property : properties) {
                    propertyMap.put(property.name(), property);
                }
            });
            return propertyMap;
        }, properties -> Either.right(properties.values().stream().toList()));
    public static final Codec<String> PLAYER_NAME = Codec.string(0, 16)
        .validate(
            name -> StringUtil.isValidPlayerName(name)
                    ? DataResult.success(name)
                    : DataResult.error(() -> "Player name contained disallowed characters: '" + name + "'")
        );
    private static final MapCodec<GameProfile> GAME_PROFILE_WITHOUT_PROPERTIES = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    UUIDUtil.AUTHLIB_CODEC.fieldOf("id").forGetter(GameProfile::getId), PLAYER_NAME.fieldOf("name").forGetter(GameProfile::getName)
                )
                .apply(instance, GameProfile::new)
    );
    public static final Codec<GameProfile> GAME_PROFILE = RecordCodecBuilder.create(
        instance -> instance.group(
                    GAME_PROFILE_WITHOUT_PROPERTIES.forGetter(Function.identity()),
                    PROPERTY_MAP.lenientOptionalFieldOf("properties", new PropertyMap()).forGetter(GameProfile::getProperties)
                )
                .apply(instance, (profile, properties) -> {
                    properties.forEach((key, property) -> profile.getProperties().put(key, property));
                    return profile;
                })
    );
    public static final Codec<String> NON_EMPTY_STRING = Codec.STRING
        .validate(string -> string.isEmpty() ? DataResult.error(() -> "Expected non-empty string") : DataResult.success(string));
    public static final Codec<Integer> CODEPOINT = Codec.STRING.comapFlatMap(string -> {
        int[] is = string.codePoints().toArray();
        return is.length != 1 ? DataResult.error(() -> "Expected one codepoint, got: " + string) : DataResult.success(is[0]);
    }, Character::toString);
    public static Codec<String> RESOURCE_PATH_CODEC = Codec.STRING
        .validate(
            path -> !ResourceLocation.isValidPath(path)
                    ? DataResult.error(() -> "Invalid string to use as a resource path element: " + path)
                    : DataResult.success(path)
        );

    public static <T> Codec<T> converter(DynamicOps<T> ops) {
        return Codec.PASSTHROUGH.xmap(dynamic -> dynamic.convert(ops).getValue(), object -> new Dynamic<>(ops, (T)object));
    }

    public static <P, I> Codec<I> intervalCodec(
        Codec<P> codec,
        String leftFieldName,
        String rightFieldName,
        BiFunction<P, P, DataResult<I>> combineFunction,
        Function<I, P> leftFunction,
        Function<I, P> rightFunction
    ) {
        Codec<I> codec2 = Codec.list(codec).comapFlatMap(list -> Util.fixedSize((List<P>)list, 2).flatMap(listx -> {
                P object = listx.get(0);
                P object2 = listx.get(1);
                return combineFunction.apply(object, object2);
            }), pair -> ImmutableList.of(leftFunction.apply((I)pair), rightFunction.apply((I)pair)));
        Codec<I> codec3 = RecordCodecBuilder.<Pair>create(
                instance -> instance.group(codec.fieldOf(leftFieldName).forGetter(Pair::getFirst), codec.fieldOf(rightFieldName).forGetter(Pair::getSecond))
                        .apply(instance, Pair::of)
            )
            .comapFlatMap(
                pair -> combineFunction.apply((P)pair.getFirst(), (P)pair.getSecond()),
                pair -> Pair.of(leftFunction.apply((I)pair), rightFunction.apply((I)pair))
            );
        Codec<I> codec4 = Codec.withAlternative(codec2, codec3);
        return Codec.either(codec, codec4)
            .comapFlatMap(either -> either.map(object -> combineFunction.apply((P)object, (P)object), DataResult::success), pair -> {
                P object = leftFunction.apply((I)pair);
                P object2 = rightFunction.apply((I)pair);
                return Objects.equals(object, object2) ? Either.left(object) : Either.right((I)pair);
            });
    }

    public static <A> ResultFunction<A> orElsePartial(A object) {
        return new ResultFunction<A>() {
            public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> dynamicOps, T object, DataResult<Pair<A, T>> dataResult) {
                MutableObject<String> mutableObject = new MutableObject<>();
                Optional<Pair<A, T>> optional = dataResult.resultOrPartial(mutableObject::setValue);
                return optional.isPresent()
                    ? dataResult
                    : DataResult.error(() -> "(" + mutableObject.getValue() + " -> using default)", Pair.of(object, object));
            }

            public <T> DataResult<T> coApply(DynamicOps<T> dynamicOps, A object, DataResult<T> dataResult) {
                return dataResult;
            }

            @Override
            public String toString() {
                return "OrElsePartial[" + object + "]";
            }
        };
    }

    public static <E> Codec<E> idResolverCodec(ToIntFunction<E> elementToRawId, IntFunction<E> rawIdToElement, int errorRawId) {
        return Codec.INT
            .flatXmap(
                rawId -> Optional.ofNullable(rawIdToElement.apply(rawId))
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unknown element id: " + rawId)),
                element -> {
                    int j = elementToRawId.applyAsInt((E)element);
                    return j == errorRawId ? DataResult.error(() -> "Element with unknown id: " + element) : DataResult.success(j);
                }
            );
    }

    public static <E> Codec<E> orCompressed(Codec<E> uncompressedCodec, Codec<E> compressedCodec) {
        return new Codec<E>() {
            public <T> DataResult<T> encode(E object, DynamicOps<T> dynamicOps, T object2) {
                return dynamicOps.compressMaps() ? compressedCodec.encode(object, dynamicOps, object2) : uncompressedCodec.encode(object, dynamicOps, object2);
            }

            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> dynamicOps, T object) {
                return dynamicOps.compressMaps() ? compressedCodec.decode(dynamicOps, object) : uncompressedCodec.decode(dynamicOps, object);
            }

            @Override
            public String toString() {
                return uncompressedCodec + " orCompressed " + compressedCodec;
            }
        };
    }

    public static <E> MapCodec<E> orCompressed(MapCodec<E> uncompressedCodec, MapCodec<E> compressedCodec) {
        return new MapCodec<E>() {
            public <T> RecordBuilder<T> encode(E object, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
                return dynamicOps.compressMaps()
                    ? compressedCodec.encode(object, dynamicOps, recordBuilder)
                    : uncompressedCodec.encode(object, dynamicOps, recordBuilder);
            }

            public <T> DataResult<E> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
                return dynamicOps.compressMaps() ? compressedCodec.decode(dynamicOps, mapLike) : uncompressedCodec.decode(dynamicOps, mapLike);
            }

            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return compressedCodec.keys(dynamicOps);
            }

            public String toString() {
                return uncompressedCodec + " orCompressed " + compressedCodec;
            }
        };
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> originalCodec, Function<E, Lifecycle> entryLifecycleGetter, Function<E, Lifecycle> lifecycleGetter) {
        return originalCodec.mapResult(new ResultFunction<E>() {
            public <T> DataResult<Pair<E, T>> apply(DynamicOps<T> dynamicOps, T object, DataResult<Pair<E, T>> dataResult) {
                return dataResult.result().map(pair -> dataResult.setLifecycle(entryLifecycleGetter.apply(pair.getFirst()))).orElse(dataResult);
            }

            public <T> DataResult<T> coApply(DynamicOps<T> dynamicOps, E object, DataResult<T> dataResult) {
                return dataResult.setLifecycle(lifecycleGetter.apply(object));
            }

            @Override
            public String toString() {
                return "WithLifecycle[" + entryLifecycleGetter + " " + lifecycleGetter + "]";
            }
        });
    }

    public static <E> Codec<E> overrideLifecycle(Codec<E> originalCodec, Function<E, Lifecycle> lifecycleGetter) {
        return overrideLifecycle(originalCodec, lifecycleGetter, lifecycleGetter);
    }

    public static <K, V> ExtraCodecs.StrictUnboundedMapCodec<K, V> strictUnboundedMap(Codec<K> keyCodec, Codec<V> elementCodec) {
        return new ExtraCodecs.StrictUnboundedMapCodec<>(keyCodec, elementCodec);
    }

    private static Codec<Integer> intRangeWithMessage(int min, int max, Function<Integer, String> messageFactory) {
        return Codec.INT
            .validate(
                value -> value.compareTo(min) >= 0 && value.compareTo(max) <= 0
                        ? DataResult.success(value)
                        : DataResult.error(() -> messageFactory.apply(value))
            );
    }

    public static Codec<Integer> intRange(int min, int max) {
        return intRangeWithMessage(min, max, value -> "Value must be within range [" + min + ";" + max + "]: " + value);
    }

    private static Codec<Float> floatRangeMinExclusiveWithMessage(float min, float max, Function<Float, String> messageFactory) {
        return Codec.FLOAT
            .validate(
                value -> value.compareTo(min) > 0 && value.compareTo(max) <= 0
                        ? DataResult.success(value)
                        : DataResult.error(() -> messageFactory.apply(value))
            );
    }

    public static <T> Codec<List<T>> nonEmptyList(Codec<List<T>> originalCodec) {
        return originalCodec.validate(list -> list.isEmpty() ? DataResult.error(() -> "List must have contents") : DataResult.success(list));
    }

    public static <T> Codec<HolderSet<T>> nonEmptyHolderSet(Codec<HolderSet<T>> originalCodec) {
        return originalCodec.validate(
            entryList -> entryList.unwrap().right().filter(List::isEmpty).isPresent()
                    ? DataResult.error(() -> "List must have contents")
                    : DataResult.success(entryList)
        );
    }

    public static <E> MapCodec<E> retrieveContext(Function<DynamicOps<?>, DataResult<E>> retriever) {
        class ContextRetrievalCodec extends MapCodec<E> {
            public <T> RecordBuilder<T> encode(E object, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
                return recordBuilder;
            }

            public <T> DataResult<E> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
                return retriever.apply(dynamicOps);
            }

            public String toString() {
                return "ContextRetrievalCodec[" + retriever + "]";
            }

            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return Stream.empty();
            }
        }

        return new ContextRetrievalCodec();
    }

    public static <E, L extends Collection<E>, T> Function<L, DataResult<L>> ensureHomogenous(Function<E, T> typeGetter) {
        return collection -> {
            Iterator<E> iterator = collection.iterator();
            if (iterator.hasNext()) {
                T object = typeGetter.apply(iterator.next());

                while (iterator.hasNext()) {
                    E object2 = iterator.next();
                    T object3 = typeGetter.apply(object2);
                    if (object3 != object) {
                        return DataResult.error(() -> "Mixed type list: element " + object2 + " had type " + object3 + ", but list is of type " + object);
                    }
                }
            }

            return DataResult.success(collection, Lifecycle.stable());
        };
    }

    public static <A> Codec<A> catchDecoderException(Codec<A> codec) {
        return Codec.of(codec, new Decoder<A>() {
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> dynamicOps, T object) {
                try {
                    return codec.decode(dynamicOps, object);
                } catch (Exception var4) {
                    return DataResult.error(() -> "Caught exception decoding " + object + ": " + var4.getMessage());
                }
            }
        });
    }

    public static Codec<TemporalAccessor> temporalCodec(DateTimeFormatter formatter) {
        return Codec.STRING.comapFlatMap(string -> {
            try {
                return DataResult.success(formatter.parse(string));
            } catch (Exception var3) {
                return DataResult.error(var3::getMessage);
            }
        }, formatter::format);
    }

    public static MapCodec<OptionalLong> asOptionalLong(MapCodec<Optional<Long>> codec) {
        return codec.xmap(toOptionalLong, fromOptionalLong);
    }

    public static <K, V> Codec<Map<K, V>> sizeLimitedMap(Codec<Map<K, V>> codec, int maxLength) {
        return codec.validate(
            map -> map.size() > maxLength
                    ? DataResult.error(() -> "Map is too long: " + map.size() + ", expected range [0-" + maxLength + "]")
                    : DataResult.success(map)
        );
    }

    public static <T> Codec<Object2BooleanMap<T>> object2BooleanMap(Codec<T> keyCodec) {
        return Codec.unboundedMap(keyCodec, Codec.BOOL).xmap(Object2BooleanOpenHashMap::new, Object2ObjectOpenHashMap::new);
    }

    @Deprecated
    public static <K, V> MapCodec<V> dispatchOptionalValue(
        String typeKey,
        String parametersKey,
        Codec<K> typeCodec,
        Function<? super V, ? extends K> typeGetter,
        Function<? super K, ? extends Codec<? extends V>> parametersCodecGetter
    ) {
        return new MapCodec<V>() {
            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return Stream.of(dynamicOps.createString(typeKey), dynamicOps.createString(parametersKey));
            }

            public <T> DataResult<V> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
                T object = mapLike.get(typeKey);
                return object == null
                    ? DataResult.error(() -> "Missing \"" + typeKey + "\" in: " + mapLike)
                    : typeCodec.decode(dynamicOps, object).flatMap(pair -> {
                        T objectx = Objects.requireNonNullElseGet(mapLike.get(parametersKey), dynamicOps::emptyMap);
                        return parametersCodecGetter.apply(pair.getFirst()).decode(dynamicOps, objectx).map(Pair::getFirst);
                    });
            }

            public <T> RecordBuilder<T> encode(V object, DynamicOps<T> dynamicOps, RecordBuilder<T> recordBuilder) {
                K object2 = (K)typeGetter.apply(object);
                recordBuilder.add(typeKey, typeCodec.encodeStart(dynamicOps, object2));
                DataResult<T> dataResult = this.encode(parametersCodecGetter.apply(object2), object, dynamicOps);
                if (dataResult.result().isEmpty() || !Objects.equals(dataResult.result().get(), dynamicOps.emptyMap())) {
                    recordBuilder.add(parametersKey, dataResult);
                }

                return recordBuilder;
            }

            private <T, V2 extends V> DataResult<T> encode(Codec<V2> codec, V value, DynamicOps<T> ops) {
                return codec.encodeStart(ops, (V2)value);
            }
        };
    }

    public static <A> Codec<Optional<A>> optionalEmptyMap(Codec<A> codec) {
        return new Codec<Optional<A>>() {
            public <T> DataResult<Pair<Optional<A>, T>> decode(DynamicOps<T> dynamicOps, T object) {
                return isEmptyMap(dynamicOps, object)
                    ? DataResult.success(Pair.of(Optional.empty(), object))
                    : codec.decode(dynamicOps, object).map(pair -> pair.mapFirst(Optional::of));
            }

            private static <T> boolean isEmptyMap(DynamicOps<T> ops, T input) {
                Optional<MapLike<T>> optional = ops.getMap(input).result();
                return optional.isPresent() && optional.get().entries().findAny().isEmpty();
            }

            public <T> DataResult<T> encode(Optional<A> optional, DynamicOps<T> dynamicOps, T object) {
                return optional.isEmpty() ? DataResult.success(dynamicOps.emptyMap()) : codec.encode(optional.get(), dynamicOps, object);
            }
        };
    }

    public static record StrictUnboundedMapCodec<K, V>(Codec<K> keyCodec, Codec<V> elementCodec) implements Codec<Map<K, V>>, BaseMapCodec<K, V> {
        public <T> DataResult<Map<K, V>> decode(DynamicOps<T> dynamicOps, MapLike<T> mapLike) {
            Builder<K, V> builder = ImmutableMap.builder();

            for (Pair<T, T> pair : mapLike.entries().toList()) {
                DataResult<K> dataResult = this.keyCodec().parse(dynamicOps, pair.getFirst());
                DataResult<V> dataResult2 = this.elementCodec().parse(dynamicOps, pair.getSecond());
                DataResult<Pair<K, V>> dataResult3 = dataResult.apply2stable(Pair::of, dataResult2);
                Optional<Error<Pair<K, V>>> optional = dataResult3.error();
                if (optional.isPresent()) {
                    String string = optional.get().message();
                    return DataResult.error(() -> dataResult.result().isPresent() ? "Map entry '" + dataResult.result().get() + "' : " + string : string);
                }

                if (!dataResult3.result().isPresent()) {
                    return DataResult.error(() -> "Empty or invalid map contents are not allowed");
                }

                Pair<K, V> pair2 = dataResult3.result().get();
                builder.put(pair2.getFirst(), pair2.getSecond());
            }

            Map<K, V> map = builder.build();
            return DataResult.success(map);
        }

        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> dynamicOps, T object) {
            return dynamicOps.getMap(object)
                .setLifecycle(Lifecycle.stable())
                .flatMap(map -> this.decode(dynamicOps, (MapLike<T>)map))
                .map(map -> Pair.of((Map<K, V>)map, object));
        }

        public <T> DataResult<T> encode(Map<K, V> map, DynamicOps<T> dynamicOps, T object) {
            return this.encode(map, dynamicOps, dynamicOps.mapBuilder()).build(object);
        }

        @Override
        public String toString() {
            return "StrictUnboundedMapCodec[" + this.keyCodec + " -> " + this.elementCodec + "]";
        }
    }

    public static record TagOrElementLocation(ResourceLocation id, boolean tag) {
        @Override
        public String toString() {
            return this.decoratedId();
        }

        private String decoratedId() {
            return this.tag ? "#" + this.id : this.id.toString();
        }
    }
}
