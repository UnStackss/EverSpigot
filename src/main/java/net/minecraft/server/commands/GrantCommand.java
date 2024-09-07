package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;

// EverSpigot
public class GrantCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_OP = new SimpleCommandExceptionType(Component.translatable("commands.op.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            // EverSpigot
            // Commands.literal("op")
            Commands.literal("grant")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .suggests(
                            (context, builder) -> {
                                PlayerList playerList = context.getSource().getServer().getPlayerList();
                                return SharedSuggestionProvider.suggest(
                                    playerList.getPlayers()
                                        .stream()
                                        .filter(player -> !playerList.isOp(player.getGameProfile()))
                                        .map(player -> player.getGameProfile().getName()),
                                    builder
                                );
                            }
                        )
                        .executes(context -> opPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets")))
                )
        );
    }

    private static int opPlayers(CommandSourceStack source, Collection<GameProfile> targets) throws CommandSyntaxException {
        PlayerList playerList = source.getServer().getPlayerList();
        int i = 0;

        for (GameProfile gameProfile : targets) {
            if (!playerList.isOp(gameProfile)) {
                playerList.op(gameProfile);
                i++;
                source.sendSuccess(() -> Component.translatable("commands.op.success", gameProfile.getName()), true); // Paper - fixes MC-253721
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_OP.create();
        } else {
            return i;
        }
    }
}
