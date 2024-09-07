package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public interface CommandFunction<T> {
    ResourceLocation id();

    InstantiatedFunction<T> instantiate(@Nullable CompoundTag arguments, CommandDispatcher<T> dispatcher) throws FunctionInstantiationException;

    private static boolean shouldConcatenateNextLine(CharSequence string) {
        int i = string.length();
        return i > 0 && string.charAt(i - 1) == '\\';
    }

    static <T extends ExecutionCommandSource<T>> CommandFunction<T> fromLines(
        ResourceLocation id, CommandDispatcher<T> dispatcher, T source, List<String> lines
    ) {
        FunctionBuilder<T> functionBuilder = new FunctionBuilder<>();

        for (int i = 0; i < lines.size(); i++) {
            int j = i + 1;
            String string = lines.get(i).trim();
            String string3;
            if (shouldConcatenateNextLine(string)) {
                StringBuilder stringBuilder = new StringBuilder(string);

                do {
                    if (++i == lines.size()) {
                        throw new IllegalArgumentException("Line continuation at end of file");
                    }

                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    String string2 = lines.get(i).trim();
                    stringBuilder.append(string2);
                    checkCommandLineLength(stringBuilder);
                } while (shouldConcatenateNextLine(stringBuilder));

                string3 = stringBuilder.toString();
            } else {
                string3 = string;
            }

            checkCommandLineLength(string3);
            StringReader stringReader = new StringReader(string3);
            if (stringReader.canRead() && stringReader.peek() != '#') {
                if (stringReader.peek() == '/') {
                    stringReader.skip();
                    if (stringReader.peek() == '/') {
                        throw new IllegalArgumentException(
                            "Unknown or invalid command '" + string3 + "' on line " + j + " (if you intended to make a comment, use '#' not '//')"
                        );
                    }

                    String string5 = stringReader.readUnquotedString();
                    throw new IllegalArgumentException(
                        "Unknown or invalid command '"
                            + string3
                            + "' on line "
                            + j
                            + " (did you mean '"
                            + string5
                            + "'? Do not use a preceding forwards slash.)"
                    );
                }

                if (stringReader.peek() == '$') {
                    functionBuilder.addMacro(string3.substring(1), j, source);
                } else {
                    try {
                        functionBuilder.addCommand(parseCommand(dispatcher, source, stringReader));
                    } catch (CommandSyntaxException var11) {
                        throw new IllegalArgumentException("Whilst parsing command on line " + j + ": " + var11.getMessage());
                    }
                }
            }
        }

        return functionBuilder.build(id);
    }

    static void checkCommandLineLength(CharSequence command) {
        if (command.length() > 2000000) {
            CharSequence charSequence = command.subSequence(0, Math.min(512, 2000000));
            throw new IllegalStateException("Command too long: " + command.length() + " characters, contents: " + charSequence + "...");
        }
    }

    static <T extends ExecutionCommandSource<T>> UnboundEntryAction<T> parseCommand(CommandDispatcher<T> dispatcher, T source, StringReader reader) throws CommandSyntaxException {
        ParseResults<T> parseResults = dispatcher.parse(reader, source);
        Commands.validateParseResults(parseResults);
        Optional<ContextChain<T>> optional = ContextChain.tryFlatten(parseResults.getContext().build(reader.getString()));
        if (optional.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.getReader());
        } else {
            return new BuildContexts.Unbound<>(reader.getString(), optional.get());
        }
    }
}
