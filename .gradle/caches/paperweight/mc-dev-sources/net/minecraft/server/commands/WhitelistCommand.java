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
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

public class WhitelistCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.alreadyOn")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.alreadyOff")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_WHITELISTED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.add.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_WHITELISTED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.remove.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("whitelist")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("on").executes(context -> enableWhitelist(context.getSource())))
                .then(Commands.literal("off").executes(context -> disableWhitelist(context.getSource())))
                .then(Commands.literal("list").executes(context -> showList(context.getSource())))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests(
                                    (context, builder) -> {
                                        PlayerList playerList = context.getSource().getServer().getPlayerList();
                                        return SharedSuggestionProvider.suggest(
                                            playerList.getPlayers()
                                                .stream()
                                                .filter(player -> !playerList.getWhiteList().isWhiteListed(player.getGameProfile()))
                                                .map(player -> player.getGameProfile().getName()),
                                            builder
                                        );
                                    }
                                )
                                .executes(context -> addPlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets")))
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests(
                                    (context, builder) -> SharedSuggestionProvider.suggest(
                                            context.getSource().getServer().getPlayerList().getWhiteListNames(), builder
                                        )
                                )
                                .executes(context -> removePlayers(context.getSource(), GameProfileArgument.getGameProfiles(context, "targets")))
                        )
                )
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
        );
    }

    private static int reload(CommandSourceStack source) {
        source.getServer().getPlayerList().reloadWhiteList();
        source.sendSuccess(() -> Component.translatable("commands.whitelist.reloaded"), true);
        source.getServer().kickUnlistedPlayers(source);
        return 1;
    }

    private static int addPlayers(CommandSourceStack source, Collection<GameProfile> targets) throws CommandSyntaxException {
        UserWhiteList userWhiteList = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (GameProfile gameProfile : targets) {
            if (!userWhiteList.isWhiteListed(gameProfile)) {
                UserWhiteListEntry userWhiteListEntry = new UserWhiteListEntry(gameProfile);
                userWhiteList.add(userWhiteListEntry);
                source.sendSuccess(() -> Component.translatable("commands.whitelist.add.success", Component.literal(gameProfile.getName())), true);
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_WHITELISTED.create();
        } else {
            return i;
        }
    }

    private static int removePlayers(CommandSourceStack source, Collection<GameProfile> targets) throws CommandSyntaxException {
        UserWhiteList userWhiteList = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (GameProfile gameProfile : targets) {
            if (userWhiteList.isWhiteListed(gameProfile)) {
                UserWhiteListEntry userWhiteListEntry = new UserWhiteListEntry(gameProfile);
                userWhiteList.remove(userWhiteListEntry);
                source.sendSuccess(() -> Component.translatable("commands.whitelist.remove.success", Component.literal(gameProfile.getName())), true);
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_NOT_WHITELISTED.create();
        } else {
            source.getServer().kickUnlistedPlayers(source);
            return i;
        }
    }

    private static int enableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        PlayerList playerList = source.getServer().getPlayerList();
        if (playerList.isUsingWhitelist()) {
            throw ERROR_ALREADY_ENABLED.create();
        } else {
            playerList.setUsingWhiteList(true);
            source.sendSuccess(() -> Component.translatable("commands.whitelist.enabled"), true);
            source.getServer().kickUnlistedPlayers(source);
            return 1;
        }
    }

    private static int disableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        PlayerList playerList = source.getServer().getPlayerList();
        if (!playerList.isUsingWhitelist()) {
            throw ERROR_ALREADY_DISABLED.create();
        } else {
            playerList.setUsingWhiteList(false);
            source.sendSuccess(() -> Component.translatable("commands.whitelist.disabled"), true);
            return 1;
        }
    }

    private static int showList(CommandSourceStack source) {
        String[] strings = source.getServer().getPlayerList().getWhiteListNames();
        if (strings.length == 0) {
            source.sendSuccess(() -> Component.translatable("commands.whitelist.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.whitelist.list", strings.length, String.join(", ", strings)), false);
        }

        return strings.length;
    }
}
