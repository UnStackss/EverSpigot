package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;

public class WeatherCommand {

    private static final int DEFAULT_TIME = -1;

    public WeatherCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("weather").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("clear").executes((commandcontext) -> {
            return WeatherCommand.setClear((CommandSourceStack) commandcontext.getSource(), -1);
        })).then(net.minecraft.commands.Commands.argument("duration", TimeArgument.time(1)).executes((commandcontext) -> {
            return WeatherCommand.setClear((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "duration"));
        })))).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("rain").executes((commandcontext) -> {
            return WeatherCommand.setRain((CommandSourceStack) commandcontext.getSource(), -1);
        })).then(net.minecraft.commands.Commands.argument("duration", TimeArgument.time(1)).executes((commandcontext) -> {
            return WeatherCommand.setRain((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "duration"));
        })))).then(((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("thunder").executes((commandcontext) -> {
            return WeatherCommand.setThunder((CommandSourceStack) commandcontext.getSource(), -1);
        })).then(net.minecraft.commands.Commands.argument("duration", TimeArgument.time(1)).executes((commandcontext) -> {
            return WeatherCommand.setThunder((CommandSourceStack) commandcontext.getSource(), IntegerArgumentType.getInteger(commandcontext, "duration"));
        }))));
    }

    private static int getDuration(CommandSourceStack source, int duration, IntProvider provider) {
        return duration == -1 ? provider.sample(source.getLevel().getRandom()) : duration; // CraftBukkit - SPIGOT-7680: per-world
    }

    private static int setClear(CommandSourceStack source, int duration) {
        source.getLevel().setWeatherParameters(WeatherCommand.getDuration(source, duration, ServerLevel.RAIN_DELAY), 0, false, false); // CraftBukkit - SPIGOT-7680: per-world
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.clear");
        }, true);
        return duration;
    }

    private static int setRain(CommandSourceStack source, int duration) {
        source.getLevel().setWeatherParameters(0, WeatherCommand.getDuration(source, duration, ServerLevel.RAIN_DURATION), true, false); // CraftBukkit - SPIGOT-7680: per-world
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.rain");
        }, true);
        return duration;
    }

    private static int setThunder(CommandSourceStack source, int duration) {
        source.getLevel().setWeatherParameters(0, WeatherCommand.getDuration(source, duration, ServerLevel.THUNDER_DURATION), true, true); // CraftBukkit - SPIGOT-7680: per-world
        source.sendSuccess(() -> {
            return Component.translatable("commands.weather.set.thunder");
        }, true);
        return duration;
    }
}
