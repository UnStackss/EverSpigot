package dev.unstackss.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

public class EverSpigotCommand {

    private static final int PERMISSION_LEVEL = 3;
    private static final int COLOR_CODE = 0x5BA6E3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ever")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("espigot").executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("everspigot").executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("evercraft").executes(context -> showHelp(context.getSource())))
        );
    }

    private static int showHelp(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (!player.hasPermissions(PERMISSION_LEVEL)) {
            source.sendFailure(Component.literal("Non hai il permesso per visualizzare i comandi."));
            return 0;
        }

        sendHelpMessage(player);
        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);

        return 1;
    }

    private static void sendHelpMessage(ServerPlayer player) {
        sendMessage(player, "§b§lEverSpigot Commands");
        sendMessage(player, "§7Lista dei comandi disponibili:");
        sendMessage(player, "§b/geoloc <nome> - Mostra l'indirizzo IP, la città e se il giocatore usa una VPN/Proxy.");
        sendMessage(player, "§b/tps - Mostra i TPS correnti del server e informazioni dettagliate.");
        sendMessage(player, "§b/memorybar - Mostra l'uso della memoria del server in tempo reale.");
        sendMessage(player, "§b/tpsbar - Mostra i TPS correnti del server in una barra in tempo reale.");
        sendMessage(player, "§b/ever - Mostra questo messaggio di aiuto.");
    }

    private static void sendMessage(ServerPlayer player, String message) {
        player.sendSystemMessage(
            Component.literal(message)
                .withStyle(style -> style.withColor(TextColor.fromRgb(COLOR_CODE)))
        );
    }
}
