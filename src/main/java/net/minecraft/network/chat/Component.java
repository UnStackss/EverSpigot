package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.Message;
import com.mojang.serialization.JsonOps;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.contents.DataSource;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
// CraftBukkit start
import java.util.stream.Stream;
// CraftBukkit end

public interface Component extends Message, FormattedText, Iterable<Component> { // CraftBukkit

    // CraftBukkit start
    default Stream<Component> stream() {
        return com.google.common.collect.Streams.concat(new Stream[]{Stream.of(this), this.getSiblings().stream().flatMap(Component::stream)});
    }

    @Override
    default Iterator<Component> iterator() {
        return this.stream().iterator();
    }
    // CraftBukkit end

    Style getStyle();

    ComponentContents getContents();

    @Override
    default String getString() {
        return FormattedText.super.getString();
    }

    default String getString(int length) {
        StringBuilder stringbuilder = new StringBuilder();

        this.visit((s) -> {
            int j = length - stringbuilder.length();

            if (j <= 0) {
                return Component.STOP_ITERATION;
            } else {
                stringbuilder.append(s.length() <= j ? s : s.substring(0, j));
                return Optional.empty();
            }
        });
        return stringbuilder.toString();
    }

    List<Component> getSiblings();

    @Nullable
    default String tryCollapseToString() {
        ComponentContents componentcontents = this.getContents();

        if (componentcontents instanceof PlainTextContents literalcontents) {
            if (this.getSiblings().isEmpty() && this.getStyle().isEmpty()) {
                return literalcontents.text();
            }
        }

        return null;
    }

    default MutableComponent plainCopy() {
        return MutableComponent.create(this.getContents());
    }

    default MutableComponent copy() {
        return new MutableComponent(this.getContents(), new ArrayList(this.getSiblings()), this.getStyle());
    }

    FormattedCharSequence getVisualOrderText();

    @Override
    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledVisitor, Style style) {
        Style chatmodifier1 = this.getStyle().applyTo(style);
        Optional<T> optional = this.getContents().visit(styledVisitor, chatmodifier1);

        if (optional.isPresent()) {
            return optional;
        } else {
            Iterator iterator = this.getSiblings().iterator();

            Optional optional1;

            do {
                if (!iterator.hasNext()) {
                    return Optional.empty();
                }

                Component ichatbasecomponent = (Component) iterator.next();

                optional1 = ichatbasecomponent.visit(styledVisitor, chatmodifier1);
            } while (!optional1.isPresent());

            return optional1;
        }
    }

    @Override
    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> visitor) {
        Optional<T> optional = this.getContents().visit(visitor);

        if (optional.isPresent()) {
            return optional;
        } else {
            Iterator iterator = this.getSiblings().iterator();

            Optional optional1;

            do {
                if (!iterator.hasNext()) {
                    return Optional.empty();
                }

                Component ichatbasecomponent = (Component) iterator.next();

                optional1 = ichatbasecomponent.visit(visitor);
            } while (!optional1.isPresent());

            return optional1;
        }
    }

    default List<Component> toFlatList() {
        return this.toFlatList(Style.EMPTY);
    }

    default List<Component> toFlatList(Style style) {
        List<Component> list = Lists.newArrayList();

        this.visit((chatmodifier1, s) -> {
            if (!s.isEmpty()) {
                list.add(Component.literal(s).withStyle(chatmodifier1));
            }

            return Optional.empty();
        }, style);
        return list;
    }

    default boolean contains(Component text) {
        if (this.equals(text)) {
            return true;
        } else {
            List<Component> list = this.toFlatList();
            List<Component> list1 = text.toFlatList(this.getStyle());

            return Collections.indexOfSubList(list, list1) != -1;
        }
    }

    static Component nullToEmpty(@Nullable String string) {
        return (Component) (string != null ? Component.literal(string) : CommonComponents.EMPTY);
    }

    static MutableComponent literal(String string) {
        return MutableComponent.create(PlainTextContents.create(string));
    }

    static MutableComponent translatable(String key) {
        return MutableComponent.create(new TranslatableContents(key, (String) null, TranslatableContents.NO_ARGS));
    }

    static MutableComponent translatable(String key, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, (String) null, args));
    }

    static MutableComponent translatableEscape(String key, Object... args) {
        for (int i = 0; i < args.length; ++i) {
            Object object = args[i];

            if (!TranslatableContents.isAllowedPrimitiveArgument(object) && !(object instanceof Component)) {
                args[i] = String.valueOf(object);
            }
        }

        return Component.translatable(key, args);
    }

    static MutableComponent translatableWithFallback(String key, @Nullable String fallback) {
        return MutableComponent.create(new TranslatableContents(key, fallback, TranslatableContents.NO_ARGS));
    }

    static MutableComponent translatableWithFallback(String key, @Nullable String fallback, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, fallback, args));
    }

    static MutableComponent empty() {
        return MutableComponent.create(PlainTextContents.EMPTY);
    }

    static MutableComponent keybind(String string) {
        return MutableComponent.create(new KeybindContents(string));
    }

    static MutableComponent nbt(String rawPath, boolean interpret, Optional<Component> separator, DataSource dataSource) {
        return MutableComponent.create(new NbtContents(rawPath, interpret, separator, dataSource));
    }

    static MutableComponent score(String name, String objective) {
        return MutableComponent.create(new ScoreContents(name, objective));
    }

    static MutableComponent selector(String pattern, Optional<Component> separator) {
        return MutableComponent.create(new SelectorContents(pattern, separator));
    }

    static Component translationArg(Date date) {
        return Component.literal(date.toString());
    }

    static Component translationArg(Message message) {
        Object object;

        if (message instanceof Component ichatbasecomponent) {
            object = ichatbasecomponent;
        } else {
            object = Component.literal(message.getString());
        }

        return (Component) object;
    }

    static Component translationArg(UUID uuid) {
        return Component.literal(uuid.toString());
    }

    static Component translationArg(ResourceLocation id) {
        return Component.literal(id.toString());
    }

    static Component translationArg(ChunkPos pos) {
        return Component.literal(pos.toString());
    }

    static Component translationArg(URI uri) {
        return Component.literal(uri.toString());
    }

    public static class SerializerAdapter implements JsonDeserializer<MutableComponent>, JsonSerializer<Component> {

        private final HolderLookup.Provider registries;

        public SerializerAdapter(HolderLookup.Provider registries) {
            this.registries = registries;
        }

        public MutableComponent deserialize(JsonElement jsonelement, Type type, JsonDeserializationContext jsondeserializationcontext) throws JsonParseException {
            return Component.Serializer.deserialize(jsonelement, this.registries);
        }

        public JsonElement serialize(Component ichatbasecomponent, Type type, JsonSerializationContext jsonserializationcontext) {
            return Component.Serializer.serialize(ichatbasecomponent, this.registries);
        }
    }

    public static class Serializer {

        private static final Gson GSON = (new GsonBuilder()).disableHtmlEscaping().create();

        private Serializer() {}

        static MutableComponent deserialize(JsonElement json, HolderLookup.Provider registries) {
            return (MutableComponent) ComponentSerialization.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);
        }

        static JsonElement serialize(Component text, HolderLookup.Provider registries) {
            return (JsonElement) ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), text).getOrThrow(JsonParseException::new);
        }

        public static String toJson(Component text, HolderLookup.Provider registries) {
            return Component.Serializer.GSON.toJson(Serializer.serialize(text, registries));
        }

        @Nullable
        public static MutableComponent fromJson(String json, HolderLookup.Provider registries) {
            JsonElement jsonelement = JsonParser.parseString(json);

            return jsonelement == null ? null : Serializer.deserialize(jsonelement, registries);
        }

        @Nullable
        public static MutableComponent fromJson(@Nullable JsonElement json, HolderLookup.Provider registries) {
            return json == null ? null : Serializer.deserialize(json, registries);
        }

        @Nullable
        public static MutableComponent fromJsonLenient(String json, HolderLookup.Provider registries) {
            JsonReader jsonreader = new JsonReader(new StringReader(json));

            jsonreader.setLenient(true);
            JsonElement jsonelement = JsonParser.parseReader(jsonreader);

            return jsonelement == null ? null : Serializer.deserialize(jsonelement, registries);
        }
    }
}
