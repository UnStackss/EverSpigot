package net.minecraft.gametest.framework;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class TestFunctionArgument implements ArgumentType<TestFunction> {
    private static final Collection<String> EXAMPLES = Arrays.asList("techtests.piston", "techtests");

    public TestFunction parse(StringReader stringReader) throws CommandSyntaxException {
        String string = stringReader.readUnquotedString();
        Optional<TestFunction> optional = GameTestRegistry.findTestFunction(string);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            Message message = Component.literal("No such test: " + string);
            throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
        }
    }

    public static TestFunctionArgument testFunctionArgument() {
        return new TestFunctionArgument();
    }

    public static TestFunction getTestFunction(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, TestFunction.class);
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return suggestTestFunction(commandContext, suggestionsBuilder);
    }

    public static <S> CompletableFuture<Suggestions> suggestTestFunction(CommandContext<S> context, SuggestionsBuilder builder) {
        Stream<String> stream = GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::testName);
        return SharedSuggestionProvider.suggest(stream, builder);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
