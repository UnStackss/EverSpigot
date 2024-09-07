package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

// EverSpigot
public class TerminateCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("terminate").requires(source -> source.hasPermission(4)).executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal("Terminating the server..."), true);
            context.getSource().getServer().halt(false);
            return 1;
        }));
    }
}
