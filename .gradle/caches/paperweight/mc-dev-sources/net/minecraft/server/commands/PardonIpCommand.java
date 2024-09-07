package net.minecraft.server.commands;

import com.google.common.net.InetAddresses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.IpBanList;

public class PardonIpCommand {
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("commands.pardonip.invalid"));
    private static final SimpleCommandExceptionType ERROR_NOT_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.pardonip.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pardon-ip")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .suggests(
                            (context, builder) -> SharedSuggestionProvider.suggest(
                                    context.getSource().getServer().getPlayerList().getIpBans().getUserList(), builder
                                )
                        )
                        .executes(context -> unban(context.getSource(), StringArgumentType.getString(context, "target")))
                )
        );
    }

    private static int unban(CommandSourceStack source, String target) throws CommandSyntaxException {
        if (!InetAddresses.isInetAddress(target)) {
            throw ERROR_INVALID.create();
        } else {
            IpBanList ipBanList = source.getServer().getPlayerList().getIpBans();
            if (!ipBanList.isBanned(target)) {
                throw ERROR_NOT_BANNED.create();
            } else {
                ipBanList.remove(target);
                source.sendSuccess(() -> Component.translatable("commands.pardonip.success", target), true);
                return 1;
            }
        }
    }
}
