package net.minecraft.server.commands;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class HelpCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.help.failed"));
    private static final SimpleCommandExceptionType ERROR_NO_PERMISSION = new SimpleCommandExceptionType(Component.translatable("commands.help.no_permission"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("help")
                .requires(source -> source.hasPermission(2)) // Default permission level
                .executes(context -> {
                    if (!context.getSource().hasPermission(2)) {
                        throw ERROR_NO_PERMISSION.create();
                    }

                    Map<CommandNode<CommandSourceStack>, String> map = dispatcher.getSmartUsage(dispatcher.getRoot(), context.getSource());

                    for (String string : map.values()) {
                        context.getSource().sendSuccess(() -> Component.literal("/" + string), false);
                    }

                    return map.size();
                })
                .then(
                    Commands.argument("command", StringArgumentType.greedyString())
                        .executes(
                            context -> {
                                if (!context.getSource().hasPermission(4)) { // EverSpigot
                                    throw ERROR_NO_PERMISSION.create();
                                }

                                ParseResults<CommandSourceStack> parseResults = dispatcher.parse(
                                    StringArgumentType.getString(context, "command"), context.getSource()
                                );
                                if (parseResults.getContext().getNodes().isEmpty()) {
                                    throw ERROR_FAILED.create();
                                } else {
                                    Map<CommandNode<CommandSourceStack>, String> map = dispatcher.getSmartUsage(
                                        Iterables.getLast(parseResults.getContext().getNodes()).getNode(), context.getSource()
                                    );

                                    for (String string : map.values()) {
                                        context.getSource()
                                            .sendSuccess(() -> Component.literal("/" + parseResults.getReader().getString() + " " + string), false);
                                    }

                                    return map.size();
                                }
                            }
                        )
                )
        );
    }
}
