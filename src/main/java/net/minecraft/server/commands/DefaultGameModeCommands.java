package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class DefaultGameModeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("defaultgamemode")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(commandContext -> setMode(commandContext.getSource(), GameModeArgument.getGameMode(commandContext, "gamemode")))
                )
        );
    }

    private static int setMode(CommandSourceStack source, GameType defaultGameMode) {
        int i = 0;
        MinecraftServer minecraftServer = source.getServer();
        minecraftServer.setDefaultGameType(defaultGameMode);
        GameType gameType = minecraftServer.getForcedGameType();
        if (gameType != null) {
            for (ServerPlayer serverPlayer : minecraftServer.getPlayerList().getPlayers()) {
                // Paper start - Expand PlayerGameModeChangeEvent
                org.bukkit.event.player.PlayerGameModeChangeEvent event = serverPlayer.setGameMode(gameType, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE, net.kyori.adventure.text.Component.empty());
                if (event != null && event.isCancelled()) {
                    source.sendSuccess(() -> io.papermc.paper.adventure.PaperAdventure.asVanilla(event.cancelMessage()), false);
                }
                // Paper end - Expand PlayerGameModeChangeEvent
                    i++;
            }
        }

        source.sendSuccess(() -> Component.translatable("commands.defaultgamemode.success", defaultGameMode.getLongDisplayName()), true);
        return i;
    }
}
