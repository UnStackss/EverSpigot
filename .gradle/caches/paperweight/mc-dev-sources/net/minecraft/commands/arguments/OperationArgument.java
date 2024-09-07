package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.ScoreAccess;

public class OperationArgument implements ArgumentType<OperationArgument.Operation> {
    private static final Collection<String> EXAMPLES = Arrays.asList("=", ">", "<");
    private static final SimpleCommandExceptionType ERROR_INVALID_OPERATION = new SimpleCommandExceptionType(
        Component.translatable("arguments.operation.invalid")
    );
    private static final SimpleCommandExceptionType ERROR_DIVIDE_BY_ZERO = new SimpleCommandExceptionType(Component.translatable("arguments.operation.div0"));

    public static OperationArgument operation() {
        return new OperationArgument();
    }

    public static OperationArgument.Operation getOperation(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, OperationArgument.Operation.class);
    }

    public OperationArgument.Operation parse(StringReader stringReader) throws CommandSyntaxException {
        if (!stringReader.canRead()) {
            throw ERROR_INVALID_OPERATION.createWithContext(stringReader);
        } else {
            int i = stringReader.getCursor();

            while (stringReader.canRead() && stringReader.peek() != ' ') {
                stringReader.skip();
            }

            return getOperation(stringReader.getString().substring(i, stringReader.getCursor()));
        }
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
        return SharedSuggestionProvider.suggest(new String[]{"=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><"}, suggestionsBuilder);
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static OperationArgument.Operation getOperation(String operator) throws CommandSyntaxException {
        return (OperationArgument.Operation)(operator.equals("><") ? (a, b) -> {
            int i = a.get();
            a.set(b.get());
            b.set(i);
        } : getSimpleOperation(operator));
    }

    private static OperationArgument.SimpleOperation getSimpleOperation(String operator) throws CommandSyntaxException {
        return switch (operator) {
            case "=" -> (a, b) -> b;
            case "+=" -> Integer::sum;
            case "-=" -> (a, b) -> a - b;
            case "*=" -> (a, b) -> a * b;
            case "/=" -> (a, b) -> {
            if (b == 0) {
                throw ERROR_DIVIDE_BY_ZERO.create();
            } else {
                return Mth.floorDiv(a, b);
            }
        };
            case "%=" -> (a, b) -> {
            if (b == 0) {
                throw ERROR_DIVIDE_BY_ZERO.create();
            } else {
                return Mth.positiveModulo(a, b);
            }
        };
            case "<" -> Math::min;
            case ">" -> Math::max;
            default -> throw ERROR_INVALID_OPERATION.create();
        };
    }

    @FunctionalInterface
    public interface Operation {
        void apply(ScoreAccess a, ScoreAccess b) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface SimpleOperation extends OperationArgument.Operation {
        int apply(int a, int b) throws CommandSyntaxException;

        @Override
        default void apply(ScoreAccess a, ScoreAccess b) throws CommandSyntaxException {
            a.set(this.apply(a.get(), b.get()));
        }
    }
}
