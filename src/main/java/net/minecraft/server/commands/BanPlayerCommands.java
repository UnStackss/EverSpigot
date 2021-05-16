package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

public class BanPlayerCommands {
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.ban.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ban")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .executes(context -> banPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets"), null))
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    context -> banPlayers(
                                            context.getSource(),
                                            GameProfileArgument.getGameProfiles(context, "targets"),
                                            MessageArgument.getMessage(context, "reason")
                                        )
                                )
                        )
                )
        );
    }

    private static int banPlayers(CommandSourceStack source, Collection<GameProfile> targets, @Nullable Component reason) throws CommandSyntaxException {
        UserBanList userBanList = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (GameProfile gameProfile : targets) {
            if (!userBanList.isBanned(gameProfile)) {
                UserBanListEntry userBanListEntry = new UserBanListEntry(
                    gameProfile, null, source.getTextName(), null, reason == null ? null : reason.getString()
                );
                userBanList.add(userBanListEntry);
                i++;
                source.sendSuccess(
                    () -> Component.translatable("commands.ban.success", Component.literal(gameProfile.getName()), userBanListEntry.getReason()), true
                );
                ServerPlayer serverPlayer = source.getServer().getPlayerList().getPlayer(gameProfile.getId());
                if (serverPlayer != null) {
                    serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"), org.bukkit.event.player.PlayerKickEvent.Cause.BANNED); // Paper - kick event cause
                }
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_BANNED.create();
        } else {
            return i;
        }
    }
}
