package org.spigotmc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TicksPerSecondCommand extends Command implements TabCompleter {

    private static final ChatColor LIGHT_CYAN_BLUE = ChatColor.AQUA;

    public TicksPerSecondCommand(String name) {
        super(name);
        this.description = "Gets the current ticks per second for the server";
        this.usageMessage = "/tps";
        this.setPermission("everspigot.command.tps");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String currentAlias, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        double[] tps = Bukkit.getTPS();
        String tps1m = formatTPS(tps[0]);
        String tps5m = formatTPS(tps[1]);
        String tps15m = formatTPS(tps[2]);

        StringBuilder message = new StringBuilder();
        message.append(LIGHT_CYAN_BLUE).append("TPS Information: ").append("\n");
        message.append(LIGHT_CYAN_BLUE).append("1 Minute: ").append(tps1m).append("\n");
        message.append(LIGHT_CYAN_BLUE).append("5 Minutes: ").append(tps5m).append("\n");
        message.append(LIGHT_CYAN_BLUE).append("15 Minutes: ").append(tps15m).append("\n");

        if (args.length > 0 && args[0].equals("details") && sender.hasPermission("everspigot.command.tpsdetails")) {
            int entitiesPerChunk = getEntitiesPerChunk();
            String lag = getServerLag();

            message.append(LIGHT_CYAN_BLUE).append("Detailed Information: ").append("\n");
            message.append(LIGHT_CYAN_BLUE).append("Entities per Chunk: ").append(entitiesPerChunk).append("\n");
            message.append(LIGHT_CYAN_BLUE).append("Server Lag: ").append(lag).append("\n");
        }

        sender.sendMessage(message.toString());
        return true;
    }

    private String formatTPS(double tps) {
        ChatColor color = (tps > 18.0) ? ChatColor.GREEN : (tps > 16.0) ? ChatColor.YELLOW : ChatColor.RED;
        return color + String.format("%.2f", tps) + (tps > 20.0 ? "*" : "");
    }

    private int getEntitiesPerChunk() {
        int totalEntities = 0;
        int totalChunks = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                totalChunks++;
                totalEntities += chunk.getEntities().length;
            }
        }
        return totalChunks > 0 ? totalEntities / totalChunks : 0;
    }


    private String getServerLag() {
        double tps = Bukkit.getTPS()[0];
        double lag = (tps < 20) ? (20 - tps) * 1000 : 0;
        return String.format("%.2f ms", lag);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("details".startsWith(args[0].toLowerCase())) {
                completions.add("details");
            }
        }
        return completions;
    }
}
