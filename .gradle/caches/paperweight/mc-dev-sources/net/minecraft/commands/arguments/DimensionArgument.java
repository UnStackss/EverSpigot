package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class DimensionArgument implements ArgumentType<ResourceLocation> {
    private static final Collection<String> EXAMPLES = Stream.of(Level.OVERWORLD, Level.NETHER)
        .map(key -> key.location().toString())
        .collect(Collectors.toList());
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(
        id -> Component.translatableEscape("argument.dimension.invalid", id)
    );

    public ResourceLocation parse(StringReader stringReader) throws CommandSyntaxException {
        return ResourceLocation.read(stringReader);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return commandContext.getSource() instanceof SharedSuggestionProvider
            ? SharedSuggestionProvider.suggestResource(
                ((SharedSuggestionProvider)commandContext.getSource()).levels().stream().map(ResourceKey::location), suggestionsBuilder
            )
            : Suggestions.empty();
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static DimensionArgument dimension() {
        return new DimensionArgument();
    }

    public static ServerLevel getDimension(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ResourceLocation resourceLocation = context.getArgument(name, ResourceLocation.class);
        ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, resourceLocation);
        ServerLevel serverLevel = context.getSource().getServer().getLevel(resourceKey);
        if (serverLevel == null) {
            throw ERROR_INVALID_VALUE.create(resourceLocation);
        } else {
            return serverLevel;
        }
    }
}
