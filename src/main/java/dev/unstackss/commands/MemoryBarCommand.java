package dev.unstackss.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

public class MemoryBarCommand {

    private static final int PERMISSION_LEVEL = 3;
    private static final int BAR_LENGTH = 20;
    private static boolean isMemoryBarVisible = false;
    private static BukkitRunnable task;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("memorybar")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(context -> toggleMemoryBar(context.getSource()))
        );
    }

    private static int toggleMemoryBar(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Player bukkitPlayer = player.getBukkitEntity();

        if (!player.hasPermissions(PERMISSION_LEVEL)) {
            source.sendFailure(Component.literal("Non hai il permesso per visualizzare l'uso della memoria."));
            return 0;
        }

        if (isMemoryBarVisible) {
            hideActionBar(bukkitPlayer);
            if (task != null) {
                task.cancel();
            }
            source.sendSuccess(() -> createColoredMessage("Barra Memoria disabilitata.", TextColor.fromRgb(0xFF0000)), false); // Red color
        } else {
            List<Plugin> plugins = List.of(bukkitPlayer.getServer().getPluginManager().getPlugins());
            if (plugins.isEmpty()) {
                source.sendFailure(Component.literal("Nessun plugin disponibile per la visualizzazione della memoria."));
                return 0;
            }
            Random random = new Random();
            Plugin randomPlugin = plugins.get(random.nextInt(plugins.size()));
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    Component memoryBar = createMemoryBar();
                    sendActionBar(bukkitPlayer, () -> memoryBar);
                }
            };
            task.runTaskTimer(Objects.requireNonNull(bukkitPlayer.getServer().getPluginManager().getPlugin(randomPlugin.getName())), 0L, 20L);

            source.sendSuccess(() -> createColoredMessage("Barra Memoria abilitata.", TextColor.fromRgb(0x00FF00)), false); // Green color
        }

        isMemoryBarVisible = !isMemoryBarVisible;
        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);

        return 1;
    }

    private static Component createMemoryBar() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double usedPercentage = (double) usedMemory / maxMemory * 100;
        int filledLength = (int) Math.round((usedPercentage / 100.0) * BAR_LENGTH);
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i < filledLength) {
                bar.append("■");
            } else {
                bar.append(" ");
            }
        }

        TextColor color;
        if (usedPercentage < 50) {
            color = TextColor.fromRgb(0x66FF99); // Light green
        } else if (usedPercentage < 75) {
            color = TextColor.fromRgb(0xFFFF00); // Yellow
        } else if (usedPercentage < 90) {
            color = TextColor.fromRgb(0xFF9933); // Orange
        } else {
            color = TextColor.fromRgb(0xFF5050); // Red
        }

        return Component.literal(bar + " (" + String.format("%.1f", usedPercentage) + "% Memoria)").withStyle(style -> style.withColor(color));
    }

    private static void sendActionBar(Player player, Supplier<Component> messageSupplier) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        Component message = messageSupplier.get();
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(message);
        craftPlayer.getHandle().connection.send(packet);
    }

    private static void hideActionBar(Player player) {
        sendActionBar(player, Component::empty);
    }

    private static Component createColoredMessage(String message, TextColor color) {
        return Component.literal(message).withStyle(style -> style.withColor(color));
    }
}
