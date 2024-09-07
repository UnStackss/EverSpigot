package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ParserUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class StyleArgument implements ArgumentType<Style> {
    private static final Collection<String> EXAMPLES = List.of("{\"bold\": true}\n");
    public static final DynamicCommandExceptionType ERROR_INVALID_JSON = new DynamicCommandExceptionType(
        style -> Component.translatableEscape("argument.style.invalid", style)
    );
    private final HolderLookup.Provider registries;

    private StyleArgument(HolderLookup.Provider registryLookup) {
        this.registries = registryLookup;
    }

    public static Style getStyle(CommandContext<CommandSourceStack> context, String style) {
        return context.getArgument(style, Style.class);
    }

    public static StyleArgument style(CommandBuildContext registryAccess) {
        return new StyleArgument(registryAccess);
    }

    public Style parse(StringReader stringReader) throws CommandSyntaxException {
        try {
            return ParserUtils.parseJson(this.registries, stringReader, Style.Serializer.CODEC);
        } catch (Exception var4) {
            String string = var4.getCause() != null ? var4.getCause().getMessage() : var4.getMessage();
            throw ERROR_INVALID_JSON.createWithContext(stringReader, string);
        }
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
