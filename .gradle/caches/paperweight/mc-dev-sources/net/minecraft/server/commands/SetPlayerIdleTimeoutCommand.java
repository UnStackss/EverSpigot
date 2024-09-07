package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetPlayerIdleTimeoutCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("setidletimeout")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("minutes", IntegerArgumentType.integer(0))
                        .executes(context -> setIdleTimeout(context.getSource(), IntegerArgumentType.getInteger(context, "minutes")))
                )
        );
    }

    private static int setIdleTimeout(CommandSourceStack source, int minutes) {
        source.getServer().setPlayerIdleTimeout(minutes);
        source.sendSuccess(() -> Component.translatable("commands.setidletimeout.success", minutes), true);
        return minutes;
    }
}
