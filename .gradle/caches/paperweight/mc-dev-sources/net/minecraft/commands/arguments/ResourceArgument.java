package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

public class ResourceArgument<T> implements ArgumentType<Holder.Reference<T>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_NOT_SUMMONABLE_ENTITY = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("entity.not_summonable", id)
    );
    public static final Dynamic2CommandExceptionType ERROR_UNKNOWN_RESOURCE = new Dynamic2CommandExceptionType(
        (element, type) -> Component.translatableEscape("argument.resource.not_found", element, type)
    );
    public static final Dynamic3CommandExceptionType ERROR_INVALID_RESOURCE_TYPE = new Dynamic3CommandExceptionType(
        (element, type, expectedType) -> Component.translatableEscape("argument.resource.invalid_type", element, type, expectedType)
    );
    final ResourceKey<? extends Registry<T>> registryKey;
    private final HolderLookup<T> registryLookup;

    public ResourceArgument(CommandBuildContext registryAccess, ResourceKey<? extends Registry<T>> registryRef) {
        this.registryKey = registryRef;
        this.registryLookup = registryAccess.lookupOrThrow(registryRef);
    }

    public static <T> ResourceArgument<T> resource(CommandBuildContext registryAccess, ResourceKey<? extends Registry<T>> registryRef) {
        return new ResourceArgument<>(registryAccess, registryRef);
    }

    public static <T> Holder.Reference<T> getResource(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> registryRef) throws CommandSyntaxException {
        Holder.Reference<T> reference = context.getArgument(name, Holder.Reference.class);
        ResourceKey<?> resourceKey = reference.key();
        if (resourceKey.isFor(registryRef)) {
            return reference;
        } else {
            throw ERROR_INVALID_RESOURCE_TYPE.create(resourceKey.location(), resourceKey.registry(), registryRef.location());
        }
    }

    public static Holder.Reference<Attribute> getAttribute(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.ATTRIBUTE);
    }

    public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.CONFIGURED_FEATURE);
    }

    public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.STRUCTURE);
    }

    public static Holder.Reference<EntityType<?>> getEntityType(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.ENTITY_TYPE);
    }

    public static Holder.Reference<EntityType<?>> getSummonableEntityType(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Holder.Reference<EntityType<?>> reference = getResource(context, name, Registries.ENTITY_TYPE);
        if (!reference.value().canSummon()) {
            throw ERROR_NOT_SUMMONABLE_ENTITY.create(reference.key().location().toString());
        } else {
            return reference;
        }
    }

    public static Holder.Reference<MobEffect> getMobEffect(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.MOB_EFFECT);
    }

    public static Holder.Reference<Enchantment> getEnchantment(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return getResource(context, name, Registries.ENCHANTMENT);
    }

    public Holder.Reference<T> parse(StringReader stringReader) throws CommandSyntaxException {
        ResourceLocation resourceLocation = ResourceLocation.read(stringReader);
        ResourceKey<T> resourceKey = ResourceKey.create(this.registryKey, resourceLocation);
        return this.registryLookup
            .get(resourceKey)
            .orElseThrow(() -> ERROR_UNKNOWN_RESOURCE.createWithContext(stringReader, resourceLocation, this.registryKey.location()));
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return SharedSuggestionProvider.suggestResource(this.registryLookup.listElementIds().map(ResourceKey::location), suggestionsBuilder);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceArgument<T>, ResourceArgument.Info<T>.Template> {
        @Override
        public void serializeToNetwork(ResourceArgument.Info<T>.Template properties, FriendlyByteBuf buf) {
            buf.writeResourceKey(properties.registryKey);
        }

        @Override
        public ResourceArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
            return new ResourceArgument.Info.Template(friendlyByteBuf.readRegistryKey());
        }

        @Override
        public void serializeToJson(ResourceArgument.Info<T>.Template properties, JsonObject json) {
            json.addProperty("registry", properties.registryKey.location().toString());
        }

        @Override
        public ResourceArgument.Info<T>.Template unpack(ResourceArgument<T> argumentType) {
            return new ResourceArgument.Info.Template(argumentType.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceArgument<T>> {
            final ResourceKey<? extends Registry<T>> registryKey;

            Template(final ResourceKey<? extends Registry<T>> registryRef) {
                this.registryKey = registryRef;
            }

            @Override
            public ResourceArgument<T> instantiate(CommandBuildContext commandBuildContext) {
                return new ResourceArgument<>(commandBuildContext, this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }
}
