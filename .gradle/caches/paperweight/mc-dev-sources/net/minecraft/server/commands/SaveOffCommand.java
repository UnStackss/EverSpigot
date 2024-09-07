package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class SaveOffCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_OFF = new SimpleCommandExceptionType(Component.translatable("commands.save.alreadyOff"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("save-off").requires(source -> source.hasPermission(4)).executes(context -> {
            CommandSourceStack commandSourceStack = context.getSource();
            boolean bl = false;

            for (ServerLevel serverLevel : commandSourceStack.getServer().getAllLevels()) {
                if (serverLevel != null && !serverLevel.noSave) {
                    serverLevel.noSave = true;
                    bl = true;
                }
            }

            if (!bl) {
                throw ERROR_ALREADY_OFF.create();
            } else {
                commandSourceStack.sendSuccess(() -> Component.translatable("commands.save.disabled"), true);
                return 1;
            }
        }));
    }
}
