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
import net.minecraft.server.players.UserBanList;

public class PardonCommand {
    private static final SimpleCommandExceptionType ERROR_NOT_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.pardon.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pardon")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .suggests(
                            (context, builder) -> SharedSuggestionProvider.suggest(
                                    context.getSource().getServer().getPlayerList().getBans().getUserList(), builder
                                )
                        )
                        .executes(context -> pardonPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets")))
                )
        );
    }

    private static int pardonPlayers(CommandSourceStack source, Collection<GameProfile> targets) throws CommandSyntaxException {
        UserBanList userBanList = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (GameProfile gameProfile : targets) {
            if (userBanList.isBanned(gameProfile)) {
                userBanList.remove(gameProfile);
                i++;
                source.sendSuccess(() -> Component.translatable("commands.pardon.success", Component.literal(gameProfile.getName())), true);
            }
        }

        if (i == 0) {
            throw ERROR_NOT_BANNED.create();
        } else {
            return i;
        }
    }
}
