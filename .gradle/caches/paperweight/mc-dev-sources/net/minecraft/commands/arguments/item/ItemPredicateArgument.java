package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemPredicateArgument implements ArgumentType<ItemPredicateArgument.Result> {
    private static final Collection<String> EXAMPLES = Arrays.asList("stick", "minecraft:stick", "#stick", "#stick{foo:'bar'}");
    static final DynamicCommandExceptionType ERROR_UNKNOWN_ITEM = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("argument.item.id.invalid", id)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_TAG = new DynamicCommandExceptionType(
        tag -> Component.translatableEscape("arguments.item.tag.unknown", tag)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_COMPONENT = new DynamicCommandExceptionType(
        component -> Component.translatableEscape("arguments.item.component.unknown", component)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_COMPONENT = new Dynamic2CommandExceptionType(
        (object, object2) -> Component.translatableEscape("arguments.item.component.malformed", object, object2)
    );
    static final DynamicCommandExceptionType ERROR_UNKNOWN_PREDICATE = new DynamicCommandExceptionType(
        predicate -> Component.translatableEscape("arguments.item.predicate.unknown", predicate)
    );
    static final Dynamic2CommandExceptionType ERROR_MALFORMED_PREDICATE = new Dynamic2CommandExceptionType(
        (object, object2) -> Component.translatableEscape("arguments.item.predicate.malformed", object, object2)
    );
    private static final ResourceLocation COUNT_ID = ResourceLocation.withDefaultNamespace("count");
    static final Map<ResourceLocation, ItemPredicateArgument.ComponentWrapper> PSEUDO_COMPONENTS = Stream.of(
            new ItemPredicateArgument.ComponentWrapper(COUNT_ID, stack -> true, MinMaxBounds.Ints.CODEC.map(range -> stack -> range.matches(stack.getCount())))
        )
        .collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.ComponentWrapper::id, check -> (ItemPredicateArgument.ComponentWrapper)check));
    static final Map<ResourceLocation, ItemPredicateArgument.PredicateWrapper> PSEUDO_PREDICATES = Stream.of(
            new ItemPredicateArgument.PredicateWrapper(COUNT_ID, MinMaxBounds.Ints.CODEC.map(range -> stack -> range.matches(stack.getCount())))
        )
        .collect(Collectors.toUnmodifiableMap(ItemPredicateArgument.PredicateWrapper::id, check -> (ItemPredicateArgument.PredicateWrapper)check));
    private final Grammar<List<Predicate<ItemStack>>> grammarWithContext;

    public ItemPredicateArgument(CommandBuildContext commandRegistryAccess) {
        ItemPredicateArgument.Context context = new ItemPredicateArgument.Context(commandRegistryAccess);
        this.grammarWithContext = ComponentPredicateParser.createGrammar(context);
    }

    public static ItemPredicateArgument itemPredicate(CommandBuildContext commandRegistryAccess) {
        return new ItemPredicateArgument(commandRegistryAccess);
    }

    public ItemPredicateArgument.Result parse(StringReader stringReader) throws CommandSyntaxException {
        return Util.allOf(this.grammarWithContext.parseForCommands(stringReader))::test;
    }

    public static ItemPredicateArgument.Result getItemPredicate(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ItemPredicateArgument.Result.class);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return this.grammarWithContext.parseForSuggestions(suggestionsBuilder);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    static record ComponentWrapper(ResourceLocation id, Predicate<ItemStack> presenceChecker, Decoder<? extends Predicate<ItemStack>> valueChecker) {
        public static <T> ItemPredicateArgument.ComponentWrapper create(ImmutableStringReader reader, ResourceLocation id, DataComponentType<T> type) throws CommandSyntaxException {
            Codec<T> codec = type.codec();
            if (codec == null) {
                throw ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(reader, id);
            } else {
                return new ItemPredicateArgument.ComponentWrapper(id, stack -> stack.has(type), codec.map(expected -> stack -> {
                        T object2 = stack.get(type);
                        return Objects.equals(expected, object2);
                    }));
            }
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, RegistryOps<Tag> ops, Tag nbt) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataResult = this.valueChecker.parse(ops, nbt);
            return (Predicate<ItemStack>)dataResult.getOrThrow(
                error -> ItemPredicateArgument.ERROR_MALFORMED_COMPONENT.createWithContext(reader, this.id.toString(), error)
            );
        }
    }

    static class Context
        implements ComponentPredicateParser.Context<Predicate<ItemStack>, ItemPredicateArgument.ComponentWrapper, ItemPredicateArgument.PredicateWrapper> {
        private final HolderLookup.RegistryLookup<Item> items;
        private final HolderLookup.RegistryLookup<DataComponentType<?>> components;
        private final HolderLookup.RegistryLookup<ItemSubPredicate.Type<?>> predicates;
        private final RegistryOps<Tag> registryOps;

        Context(HolderLookup.Provider registryLookup) {
            this.items = registryLookup.lookupOrThrow(Registries.ITEM);
            this.components = registryLookup.lookupOrThrow(Registries.DATA_COMPONENT_TYPE);
            this.predicates = registryLookup.lookupOrThrow(Registries.ITEM_SUB_PREDICATE_TYPE);
            this.registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        }

        @Override
        public Predicate<ItemStack> forElementType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
            Holder.Reference<Item> reference = this.items
                .get(ResourceKey.create(Registries.ITEM, resourceLocation))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_ITEM.createWithContext(immutableStringReader, resourceLocation));
            return stack -> stack.is(reference);
        }

        @Override
        public Predicate<ItemStack> forTagType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
            HolderSet<Item> holderSet = this.items
                .get(TagKey.create(Registries.ITEM, resourceLocation))
                .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_TAG.createWithContext(immutableStringReader, resourceLocation));
            return stack -> stack.is(holderSet);
        }

        @Override
        public ItemPredicateArgument.ComponentWrapper lookupComponentType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
            ItemPredicateArgument.ComponentWrapper componentWrapper = ItemPredicateArgument.PSEUDO_COMPONENTS.get(resourceLocation);
            if (componentWrapper != null) {
                return componentWrapper;
            } else {
                DataComponentType<?> dataComponentType = this.components
                    .get(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, resourceLocation))
                    .map(Holder::value)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_COMPONENT.createWithContext(immutableStringReader, resourceLocation));
                return ItemPredicateArgument.ComponentWrapper.create(immutableStringReader, resourceLocation, dataComponentType);
            }
        }

        @Override
        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper check, Tag nbt) throws CommandSyntaxException {
            return check.decode(reader, this.registryOps, nbt);
        }

        @Override
        public Predicate<ItemStack> createComponentTest(ImmutableStringReader reader, ItemPredicateArgument.ComponentWrapper check) {
            return check.presenceChecker;
        }

        @Override
        public ItemPredicateArgument.PredicateWrapper lookupPredicateType(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws CommandSyntaxException {
            ItemPredicateArgument.PredicateWrapper predicateWrapper = ItemPredicateArgument.PSEUDO_PREDICATES.get(resourceLocation);
            return predicateWrapper != null
                ? predicateWrapper
                : this.predicates
                    .get(ResourceKey.create(Registries.ITEM_SUB_PREDICATE_TYPE, resourceLocation))
                    .map(ItemPredicateArgument.PredicateWrapper::new)
                    .orElseThrow(() -> ItemPredicateArgument.ERROR_UNKNOWN_PREDICATE.createWithContext(immutableStringReader, resourceLocation));
        }

        @Override
        public Predicate<ItemStack> createPredicateTest(
            ImmutableStringReader immutableStringReader, ItemPredicateArgument.PredicateWrapper predicateWrapper, Tag tag
        ) throws CommandSyntaxException {
            return predicateWrapper.decode(immutableStringReader, this.registryOps, tag);
        }

        @Override
        public Stream<ResourceLocation> listElementTypes() {
            return this.items.listElementIds().map(ResourceKey::location);
        }

        @Override
        public Stream<ResourceLocation> listTagTypes() {
            return this.items.listTagIds().map(TagKey::location);
        }

        @Override
        public Stream<ResourceLocation> listComponentTypes() {
            return Stream.concat(
                ItemPredicateArgument.PSEUDO_COMPONENTS.keySet().stream(),
                this.components.listElements().filter(entry -> !entry.value().isTransient()).map(entry -> entry.key().location())
            );
        }

        @Override
        public Stream<ResourceLocation> listPredicateTypes() {
            return Stream.concat(ItemPredicateArgument.PSEUDO_PREDICATES.keySet().stream(), this.predicates.listElementIds().map(ResourceKey::location));
        }

        @Override
        public Predicate<ItemStack> negate(Predicate<ItemStack> predicate) {
            return predicate.negate();
        }

        @Override
        public Predicate<ItemStack> anyOf(List<Predicate<ItemStack>> list) {
            return Util.anyOf(list);
        }
    }

    static record PredicateWrapper(ResourceLocation id, Decoder<? extends Predicate<ItemStack>> type) {
        public PredicateWrapper(Holder.Reference<ItemSubPredicate.Type<?>> type) {
            this(type.key().location(), type.value().codec().map(predicate -> predicate::matches));
        }

        public Predicate<ItemStack> decode(ImmutableStringReader reader, RegistryOps<Tag> ops, Tag nbt) throws CommandSyntaxException {
            DataResult<? extends Predicate<ItemStack>> dataResult = this.type.parse(ops, nbt);
            return (Predicate<ItemStack>)dataResult.getOrThrow(
                error -> ItemPredicateArgument.ERROR_MALFORMED_PREDICATE.createWithContext(reader, this.id.toString(), error)
            );
        }
    }

    public interface Result extends Predicate<ItemStack> {
    }
}
