package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringRepresentable;

public class ComponentSerialization {
    public static final Codec<Component> CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_STREAM_CODEC.apply(
        ByteBufCodecs::optional
    );
    public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CODEC);
    public static final Codec<Component> FLAT_CODEC = flatCodec(Integer.MAX_VALUE);

    public static Codec<Component> flatCodec(int maxSerializedLength) {
        final Codec<String> codec = Codec.string(0, maxSerializedLength);
        return new Codec<Component>() {
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> dynamicOps, T object) {
                DynamicOps<JsonElement> dynamicOps2 = asJsonOps(dynamicOps);
                return codec.decode(dynamicOps, object).flatMap(pair -> {
                    try {
                        JsonElement jsonElement = JsonParser.parseString(pair.getFirst());
                        return ComponentSerialization.CODEC.parse(dynamicOps2, jsonElement).map(text -> Pair.of(text, (T)pair.getSecond()));
                    } catch (JsonParseException var3x) {
                        return DataResult.error(var3x::getMessage);
                    }
                });
            }

            public <T> DataResult<T> encode(Component component, DynamicOps<T> dynamicOps, T object) {
                DynamicOps<JsonElement> dynamicOps2 = asJsonOps(dynamicOps);
                return ComponentSerialization.CODEC.encodeStart(dynamicOps2, component).flatMap(json -> {
                    try {
                        return codec.encodeStart(dynamicOps, GsonHelper.toStableString(json));
                    } catch (IllegalArgumentException var4x) {
                        return DataResult.error(var4x::getMessage);
                    }
                });
            }

            private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> ops) {
                return (DynamicOps<JsonElement>)(ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE);
            }
        };
    }

    private static MutableComponent createFromList(List<Component> texts) {
        MutableComponent mutableComponent = texts.get(0).copy();

        for (int i = 1; i < texts.size(); i++) {
            mutableComponent.append(texts.get(i));
        }

        return mutableComponent;
    }

    public static <T extends StringRepresentable, E> MapCodec<E> createLegacyComponentMatcher(
        T[] types, Function<T, MapCodec<? extends E>> typeToCodec, Function<E, T> valueToType, String dispatchingKey
    ) {
        MapCodec<E> mapCodec = new ComponentSerialization.FuzzyCodec<>(
            Stream.<T>of(types).map(typeToCodec).toList(), object -> typeToCodec.apply(valueToType.apply(object))
        );
        Codec<T> codec = StringRepresentable.fromValues((Supplier<T[]>)(() -> types));
        MapCodec<E> mapCodec2 = codec.dispatchMap(dispatchingKey, valueToType, typeToCodec);
        MapCodec<E> mapCodec3 = new ComponentSerialization.StrictEither<>(dispatchingKey, mapCodec2, mapCodec);
        return ExtraCodecs.orCompressed(mapCodec3, mapCodec2);
    }

    private static Codec<Component> createCodec(Codec<Component> selfCodec) {
        ComponentContents.Type<?>[] types = new ComponentContents.Type[]{
            PlainTextContents.TYPE, TranslatableContents.TYPE, KeybindContents.TYPE, ScoreContents.TYPE, SelectorContents.TYPE, NbtContents.TYPE
        };
        MapCodec<ComponentContents> mapCodec = createLegacyComponentMatcher(types, ComponentContents.Type::codec, ComponentContents::type, "type");
        Codec<Component> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                        mapCodec.forGetter(Component::getContents),
                        ExtraCodecs.nonEmptyList(selfCodec.listOf()).optionalFieldOf("extra", List.of()).forGetter(Component::getSiblings),
                        Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)
                    )
                    .apply(instance, MutableComponent::new)
        );
        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(selfCodec.listOf())), codec)
            .xmap(either -> either.map(either2 -> either2.map(Component::literal, ComponentSerialization::createFromList), text -> (Component)text), text -> {
                String string = text.tryCollapseToString();
                return string != null ? Either.left(Either.left(string)) : Either.right(text);
            });
    }

    static class FuzzyCodec<T> extends MapCodec<T> {
        private final List<MapCodec<? extends T>> codecs;
        private final Function<T, MapEncoder<? extends T>> encoderGetter;

        public FuzzyCodec(List<MapCodec<? extends T>> codecs, Function<T, MapEncoder<? extends T>> codecGetter) {
            this.codecs = codecs;
            this.encoderGetter = codecGetter;
        }

        public <S> DataResult<T> decode(DynamicOps<S> dynamicOps, MapLike<S> mapLike) {
            for (MapDecoder<? extends T> mapDecoder : this.codecs) {
                DataResult<? extends T> dataResult = mapDecoder.decode(dynamicOps, mapLike);
                if (dataResult.result().isPresent()) {
                    return (DataResult<T>)dataResult;
                }
            }

            return DataResult.error(() -> "No matching codec found");
        }

        public <S> RecordBuilder<S> encode(T object, DynamicOps<S> dynamicOps, RecordBuilder<S> recordBuilder) {
            MapEncoder<T> mapEncoder = (MapEncoder<T>)this.encoderGetter.apply(object);
            return mapEncoder.encode(object, dynamicOps, recordBuilder);
        }

        public <S> Stream<S> keys(DynamicOps<S> dynamicOps) {
            return this.codecs.stream().flatMap(codec -> codec.keys(dynamicOps)).distinct();
        }

        public String toString() {
            return "FuzzyCodec[" + this.codecs + "]";
        }
    }

    static class StrictEither<T> extends MapCodec<T> {
        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public StrictEither(String dispatchingKey, MapCodec<T> withKeyCodec, MapCodec<T> withoutKeyCodec) {
            this.typeFieldName = dispatchingKey;
            this.typed = withKeyCodec;
            this.fuzzy = withoutKeyCodec;
        }

        public <O> DataResult<T> decode(DynamicOps<O> dynamicOps, MapLike<O> mapLike) {
            return mapLike.get(this.typeFieldName) != null ? this.typed.decode(dynamicOps, mapLike) : this.fuzzy.decode(dynamicOps, mapLike);
        }

        public <O> RecordBuilder<O> encode(T object, DynamicOps<O> dynamicOps, RecordBuilder<O> recordBuilder) {
            return this.fuzzy.encode(object, dynamicOps, recordBuilder);
        }

        public <T1> Stream<T1> keys(DynamicOps<T1> dynamicOps) {
            return Stream.concat(this.typed.keys(dynamicOps), this.fuzzy.keys(dynamicOps)).distinct();
        }
    }
}
