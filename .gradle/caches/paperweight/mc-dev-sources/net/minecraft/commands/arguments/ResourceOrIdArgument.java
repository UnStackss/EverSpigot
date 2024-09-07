package net.minecraft.commands.arguments;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class ResourceOrIdArgument<T> implements ArgumentType<Holder<T>> {
    private static final Collection<String> EXAMPLES = List.of("foo", "foo:bar", "012", "{}", "true");
    public static final DynamicCommandExceptionType ERROR_FAILED_TO_PARSE = new DynamicCommandExceptionType(
        argument -> Component.translatableEscape("argument.resource_or_id.failed_to_parse", argument)
    );
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.resource_or_id.invalid"));
    private final HolderLookup.Provider registryLookup;
    private final boolean hasRegistry;
    private final Codec<Holder<T>> codec;

    protected ResourceOrIdArgument(CommandBuildContext registryAccess, ResourceKey<Registry<T>> registry, Codec<Holder<T>> entryCodec) {
        this.registryLookup = registryAccess;
        this.hasRegistry = registryAccess.lookup(registry).isPresent();
        this.codec = entryCodec;
    }

    public static ResourceOrIdArgument.LootTableArgument lootTable(CommandBuildContext registryAccess) {
        return new ResourceOrIdArgument.LootTableArgument(registryAccess);
    }

    public static Holder<LootTable> getLootTable(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return getResource(context, argument);
    }

    public static ResourceOrIdArgument.LootModifierArgument lootModifier(CommandBuildContext registryAccess) {
        return new ResourceOrIdArgument.LootModifierArgument(registryAccess);
    }

    public static Holder<LootItemFunction> getLootModifier(CommandContext<CommandSourceStack> context, String argument) {
        return getResource(context, argument);
    }

    public static ResourceOrIdArgument.LootPredicateArgument lootPredicate(CommandBuildContext registryAccess) {
        return new ResourceOrIdArgument.LootPredicateArgument(registryAccess);
    }

    public static Holder<LootItemCondition> getLootPredicate(CommandContext<CommandSourceStack> context, String argument) {
        return getResource(context, argument);
    }

    private static <T> Holder<T> getResource(CommandContext<CommandSourceStack> context, String argument) {
        return context.getArgument(argument, Holder.class);
    }

    @Nullable
    public Holder<T> parse(StringReader stringReader) throws CommandSyntaxException {
        Tag tag = parseInlineOrId(stringReader);
        if (!this.hasRegistry) {
            return null;
        } else {
            RegistryOps<Tag> registryOps = this.registryLookup.createSerializationContext(NbtOps.INSTANCE);
            return this.codec.parse(registryOps, tag).getOrThrow(argument -> ERROR_FAILED_TO_PARSE.createWithContext(stringReader, argument));
        }
    }

    @VisibleForTesting
    static Tag parseInlineOrId(StringReader stringReader) throws CommandSyntaxException {
        int i = stringReader.getCursor();
        Tag tag = new TagParser(stringReader).readValue();
        if (hasConsumedWholeArg(stringReader)) {
            return tag;
        } else {
            stringReader.setCursor(i);
            ResourceLocation resourceLocation = ResourceLocation.read(stringReader);
            if (hasConsumedWholeArg(stringReader)) {
                return StringTag.valueOf(resourceLocation.toString());
            } else {
                stringReader.setCursor(i);
                throw ERROR_INVALID.createWithContext(stringReader);
            }
        }
    }

    private static boolean hasConsumedWholeArg(StringReader stringReader) {
        return !stringReader.canRead() || stringReader.peek() == ' ';
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class LootModifierArgument extends ResourceOrIdArgument<LootItemFunction> {
        protected LootModifierArgument(CommandBuildContext registryAccess) {
            super(registryAccess, Registries.ITEM_MODIFIER, LootItemFunctions.CODEC);
        }
    }

    public static class LootPredicateArgument extends ResourceOrIdArgument<LootItemCondition> {
        protected LootPredicateArgument(CommandBuildContext registryAccess) {
            super(registryAccess, Registries.PREDICATE, LootItemCondition.CODEC);
        }
    }

    public static class LootTableArgument extends ResourceOrIdArgument<LootTable> {
        protected LootTableArgument(CommandBuildContext registryAccess) {
            super(registryAccess, Registries.LOOT_TABLE, LootTable.CODEC);
        }
    }
}
