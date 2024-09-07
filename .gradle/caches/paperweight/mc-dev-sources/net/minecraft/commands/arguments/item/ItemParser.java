package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableObject;

public class ItemParser {
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("argument.item.id.invalid", id)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("arguments.item.component.unknown", id)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (type, error) -> Component.translatableEscape("arguments.item.component.malformed", type, error)
    );
    static final SimpleCommandExceptionType ERROR_EXPECTED_COMPONENT = new SimpleCommandExceptionType(
        Component.translatable("arguments.item.component.expected")
    );
    static final DynamicCommandExceptionType ERROR_REPEATED_COMPONENT = new DynamicCommandExceptionType(
        type -> Component.translatableEscape("arguments.item.component.repeated", type)
    );
    private static final DynamicCommandExceptionType ERROR_MALFORMED_ITEM = new DynamicCommandExceptionType(
        error -> Component.translatableEscape("arguments.item.malformed", error)
    );
    public static final char SYNTAX_START_COMPONENTS = '[';
    public static final char SYNTAX_END_COMPONENTS = ']';
    public static final char SYNTAX_COMPONENT_SEPARATOR = ',';
    public static final char SYNTAX_COMPONENT_ASSIGNMENT = '=';
    public static final char SYNTAX_REMOVED_COMPONENT = '!';
    static final Function<SuggestionsBuilder, CompletableFuture<Suggestions>> SUGGEST_NOTHING = SuggestionsBuilder::buildFuture;
    final HolderLookup.RegistryLookup<Item> items;
    final DynamicOps<Tag> registryOps;

    public ItemParser(HolderLookup.Provider registryLookup) {
        this.items = registryLookup.lookupOrThrow(Registries.ITEM);
        this.registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
    }

    public ItemParser.ItemResult parse(StringReader reader) throws CommandSyntaxException {
        final MutableObject<Holder<Item>> mutableObject = new MutableObject<>();
        final DataComponentPatch.Builder builder = DataComponentPatch.builder();
        this.parse(reader, new ItemParser.Visitor() {
            @Override
            public void visitItem(Holder<Item> item) {
                mutableObject.setValue(item);
            }

            @Override
            public <T> void visitComponent(DataComponentType<T> type, T value) {
                builder.set(type, value);
            }

            @Override
            public <T> void visitRemovedComponent(DataComponentType<T> type) {
                builder.remove(type);
            }
        });
        Holder<Item> holder = Objects.requireNonNull(mutableObject.getValue(), "Parser gave no item");
        DataComponentPatch dataComponentPatch = builder.build();
        validateComponents(reader, holder, dataComponentPatch);
        return new ItemParser.ItemResult(holder, dataComponentPatch);
    }

    private static void validateComponents(StringReader reader, Holder<Item> item, DataComponentPatch components) throws CommandSyntaxException {
        DataComponentMap dataComponentMap = PatchedDataComponentMap.fromPatch(item.value().components(), components);
        DataResult<Unit> dataResult = ItemStack.validateComponents(dataComponentMap);
        dataResult.getOrThrow(error -> ERROR_MALFORMED_ITEM.createWithContext(reader, error));
    }

    public void parse(StringReader reader, ItemParser.Visitor callbacks) throws CommandSyntaxException {
        int i = reader.getCursor();

        try {
            new ItemParser.State(reader, callbacks).parse();
        } catch (CommandSyntaxException var5) {
            reader.setCursor(i);
            throw var5;
        }
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder) {
        StringReader stringReader = new StringReader(builder.getInput());
        stringReader.setCursor(builder.getStart());
        ItemParser.SuggestionsVisitor suggestionsVisitor = new ItemParser.SuggestionsVisitor();
        ItemParser.State state = new ItemParser.State(stringReader, suggestionsVisitor);

        try {
            state.parse();
        } catch (CommandSyntaxException var6) {
        }

        return suggestionsVisitor.resolveSuggestions(builder, stringReader);
    }

    public static record ItemResult(Holder<Item> item, DataComponentPatch components) {
    }

    class State {
        private final StringReader reader;
        private final ItemParser.Visitor visitor;

        State(final StringReader reader, final ItemParser.Visitor callbacks) {
            this.reader = reader;
            this.visitor = callbacks;
        }

        public void parse() throws CommandSyntaxException {
            this.visitor.visitSuggestions(this::suggestItem);
            this.readItem();
            this.visitor.visitSuggestions(this::suggestStartComponents);
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                this.readComponents();
            }
        }

        private void readItem() throws CommandSyntaxException {
            int i = this.reader.getCursor();
            ResourceLocation resourceLocation = ResourceLocation.read(this.reader);
            this.visitor.visitItem(ItemParser.this.items.get(ResourceKey.create(Registries.ITEM, resourceLocation)).orElseThrow(() -> {
                this.reader.setCursor(i);
                return ItemParser.ERROR_UNKNOWN_ITEM.createWithContext(this.reader, resourceLocation);
            }));
        }

        private void readComponents() throws CommandSyntaxException {
            this.reader.expect('[');
            this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
            Set<DataComponentType<?>> set = new ReferenceArraySet<>();

            while (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                if (this.reader.canRead() && this.reader.peek() == '!') {
                    this.reader.skip();
                    this.visitor.visitSuggestions(this::suggestComponent);
                    DataComponentType<?> dataComponentType = readComponentType(this.reader);
                    if (!set.add(dataComponentType)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(dataComponentType);
                    }

                    this.visitor.visitRemovedComponent(dataComponentType);
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                } else {
                    DataComponentType<?> dataComponentType2 = readComponentType(this.reader);
                    if (!set.add(dataComponentType2)) {
                        throw ItemParser.ERROR_REPEATED_COMPONENT.create(dataComponentType2);
                    }

                    this.visitor.visitSuggestions(this::suggestAssignment);
                    this.reader.skipWhitespace();
                    this.reader.expect('=');
                    this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
                    this.reader.skipWhitespace();
                    this.readComponent(dataComponentType2);
                    this.reader.skipWhitespace();
                }

                this.visitor.visitSuggestions(this::suggestNextOrEndComponents);
                if (!this.reader.canRead() || this.reader.peek() != ',') {
                    break;
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.visitor.visitSuggestions(this::suggestComponentAssignmentOrRemoval);
                if (!this.reader.canRead()) {
                    throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(this.reader);
                }
            }

            this.reader.expect(']');
            this.visitor.visitSuggestions(ItemParser.SUGGEST_NOTHING);
        }

        public static DataComponentType<?> readComponentType(StringReader reader) throws CommandSyntaxException {
            if (!reader.canRead()) {
                throw ItemParser.ERROR_EXPECTED_COMPONENT.createWithContext(reader);
            } else {
                int i = reader.getCursor();
                ResourceLocation resourceLocation = ResourceLocation.read(reader);
                DataComponentType<?> dataComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(resourceLocation);
                if (dataComponentType != null && !dataComponentType.isTransient()) {
                    return dataComponentType;
                } else {
                    reader.setCursor(i);
                    throw ItemParser.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, resourceLocation);
                }
            }
        }

        private <T> void readComponent(DataComponentType<T> type) throws CommandSyntaxException {
            int i = this.reader.getCursor();
            Tag tag = new TagParser(this.reader).readValue();
            DataResult<T> dataResult = type.codecOrThrow().parse(ItemParser.this.registryOps, tag);
            this.visitor.visitComponent(type, dataResult.getOrThrow(error -> {
                this.reader.setCursor(i);
                return ItemParser.ERROR_MALFORMED_COMPONENT.createWithContext(this.reader, type.toString(), error);
            }));
        }

        private CompletableFuture<Suggestions> suggestStartComponents(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf('['));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestNextOrEndComponents(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf(','));
                builder.suggest(String.valueOf(']'));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestAssignment(SuggestionsBuilder builder) {
            if (builder.getRemaining().isEmpty()) {
                builder.suggest(String.valueOf('='));
            }

            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestItem(SuggestionsBuilder builder) {
            return SharedSuggestionProvider.suggestResource(ItemParser.this.items.listElementIds().map(ResourceKey::location), builder);
        }

        private CompletableFuture<Suggestions> suggestComponentAssignmentOrRemoval(SuggestionsBuilder builder) {
            builder.suggest(String.valueOf('!'));
            return this.suggestComponent(builder, String.valueOf('='));
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder builder) {
            return this.suggestComponent(builder, "");
        }

        private CompletableFuture<Suggestions> suggestComponent(SuggestionsBuilder builder, String suffix) {
            String string = builder.getRemaining().toLowerCase(Locale.ROOT);
            SharedSuggestionProvider.filterResources(BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet(), string, entry -> entry.getKey().location(), entry -> {
                DataComponentType<?> dataComponentType = entry.getValue();
                if (dataComponentType.codec() != null) {
                    ResourceLocation resourceLocation = entry.getKey().location();
                    builder.suggest(resourceLocation + suffix);
                }
            });
            return builder.buildFuture();
        }
    }

    static class SuggestionsVisitor implements ItemParser.Visitor {
        private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions = ItemParser.SUGGEST_NOTHING;

        @Override
        public void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestor) {
            this.suggestions = suggestor;
        }

        public CompletableFuture<Suggestions> resolveSuggestions(SuggestionsBuilder builder, StringReader reader) {
            return this.suggestions.apply(builder.createOffset(reader.getCursor()));
        }
    }

    public interface Visitor {
        default void visitItem(Holder<Item> item) {
        }

        default <T> void visitComponent(DataComponentType<T> type, T value) {
        }

        default <T> void visitRemovedComponent(DataComponentType<T> type) {
        }

        default void visitSuggestions(Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestor) {
        }
    }
}
