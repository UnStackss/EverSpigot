package net.minecraft.commands.arguments;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public class StringRepresentableArgument<T extends Enum<T> & StringRepresentable> implements ArgumentType<T> {
    private static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(
        value -> Component.translatableEscape("argument.enum.invalid", value)
    );
    private final Codec<T> codec;
    private final Supplier<T[]> values;

    protected StringRepresentableArgument(Codec<T> codec, Supplier<T[]> valuesSupplier) {
        this.codec = codec;
        this.values = valuesSupplier;
    }

    public T parse(StringReader stringReader) throws CommandSyntaxException {
        String string = stringReader.readUnquotedString();
        return this.codec
            .parse(JsonOps.INSTANCE, new JsonPrimitive(string))
            .result()
            .orElseThrow(() -> ERROR_INVALID_VALUE.createWithContext(stringReader, string));
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return SharedSuggestionProvider.suggest(
            Arrays.<Enum>stream((Enum[])this.values.get())
                .map(enum_ -> ((StringRepresentable)enum_).getSerializedName())
                .map(this::convertId)
                .collect(Collectors.toList()),
            suggestionsBuilder
        );
    }

    public Collection<String> getExamples() {
        return Arrays.<Enum>stream((Enum[])this.values.get())
            .map(enum_ -> ((StringRepresentable)enum_).getSerializedName())
            .map(this::convertId)
            .limit(2L)
            .collect(Collectors.toList());
    }

    protected String convertId(String name) {
        return name;
    }
}
