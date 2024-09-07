package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;

public class TransferCommand {
    private static final SimpleCommandExceptionType ERROR_NO_PLAYERS = new SimpleCommandExceptionType(
        Component.translatable("commands.transfer.error.no_players")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("transfer")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("hostname", StringArgumentType.string())
                        .executes(
                            context -> transfer(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "hostname"),
                                    25565,
                                    List.of(context.getSource().getPlayerOrException())
                                )
                        )
                        .then(
                            Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(
                                    context -> transfer(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "hostname"),
                                            IntegerArgumentType.getInteger(context, "port"),
                                            List.of(context.getSource().getPlayerOrException())
                                        )
                                )
                                .then(
                                    Commands.argument("players", EntityArgument.players())
                                        .executes(
                                            context -> transfer(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "hostname"),
                                                    IntegerArgumentType.getInteger(context, "port"),
                                                    EntityArgument.getPlayers(context, "players")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int transfer(CommandSourceStack source, String host, int port, Collection<ServerPlayer> players) throws CommandSyntaxException {
        if (players.isEmpty()) {
            throw ERROR_NO_PLAYERS.create();
        } else {
            for (ServerPlayer serverPlayer : players) {
                serverPlayer.connection.send(new ClientboundTransferPacket(host, port));
            }

            if (players.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable("commands.transfer.success.single", players.iterator().next().getDisplayName(), host, port), true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.transfer.success.multiple", players.size(), host, port), true);
            }

            return players.size();
        }
    }
}
