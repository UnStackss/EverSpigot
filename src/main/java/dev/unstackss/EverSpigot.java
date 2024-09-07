package dev.unstackss;

import com.mojang.brigadier.CommandDispatcher;
import dev.unstackss.commands.EverSpigotCommand;
import dev.unstackss.commands.GeoLocCommand;
import dev.unstackss.commands.MemoryBarCommand;
import dev.unstackss.commands.TpsBarCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

public record EverSpigot(MinecraftServer server) {

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        GeoLocCommand.register(dispatcher);
        EverSpigotCommand.register(dispatcher);
        TpsBarCommand.register(dispatcher);
        MemoryBarCommand.register(dispatcher);
    }

    public static void onServerStarting(MinecraftServer server) {
        registerCommands(server.getCommands().getDispatcher());
    }
}
