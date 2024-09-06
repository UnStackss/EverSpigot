package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class KickCommand {
    private static final SimpleCommandExceptionType ERROR_KICKING_OWNER = new SimpleCommandExceptionType(Component.translatable("commands.kick.owner.failed"));
    private static final SimpleCommandExceptionType ERROR_SINGLEPLAYER = new SimpleCommandExceptionType(
        Component.translatable("commands.kick.singleplayer.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("kick")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("targets", EntityArgument.players())
                        .executes(
                            context -> kickPlayers(
                                    context.getSource(), EntityArgument.getPlayers(context, "targets"), Component.translatable("multiplayer.disconnect.kicked")
                                )
                        )
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    context -> kickPlayers(
                                            context.getSource(), EntityArgument.getPlayers(context, "targets"), MessageArgument.getMessage(context, "reason")
                                        )
                                )
                        )
                )
        );
    }

    private static int kickPlayers(CommandSourceStack source, Collection<ServerPlayer> targets, Component reason) throws CommandSyntaxException {
        if (!source.getServer().isPublished()) {
            throw ERROR_SINGLEPLAYER.create();
        } else {
            int i = 0;

            for (ServerPlayer serverPlayer : targets) {
                if (!source.getServer().isSingleplayerOwner(serverPlayer.getGameProfile())) {
                    serverPlayer.connection.disconnect(reason);
                    source.sendSuccess(() -> Component.translatable("commands.kick.success", serverPlayer.getDisplayName(), reason), true);
                    i++;
                }
            }

            if (i == 0) {
                throw ERROR_KICKING_OWNER.create();
            } else {
                return i;
            }
        }
    }
}
