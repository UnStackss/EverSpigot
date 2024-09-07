package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.TimeUtil;

public class TickCommand {
    private static final float MAX_TICKRATE = 10000.0F;
    private static final String DEFAULT_TICKRATE = String.valueOf(20);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tick")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("query").executes(context -> tickQuery(context.getSource())))
                .then(
                    Commands.literal("rate")
                        .then(
                            Commands.argument("rate", FloatArgumentType.floatArg(1.0F, 10000.0F))
                                .suggests((context, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{DEFAULT_TICKRATE}, suggestionsBuilder))
                                .executes(context -> setTickingRate(context.getSource(), FloatArgumentType.getFloat(context, "rate")))
                        )
                )
                .then(
                    Commands.literal("step")
                        .executes(context -> step(context.getSource(), 1))
                        .then(Commands.literal("stop").executes(context -> stopStepping(context.getSource())))
                        .then(
                            Commands.argument("time", TimeArgument.time(1))
                                .suggests((context, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"1t", "1s"}, suggestionsBuilder))
                                .executes(context -> step(context.getSource(), IntegerArgumentType.getInteger(context, "time")))
                        )
                )
                .then(
                    Commands.literal("sprint")
                        .then(Commands.literal("stop").executes(context -> stopSprinting(context.getSource())))
                        .then(
                            Commands.argument("time", TimeArgument.time(1))
                                .suggests(
                                    (context, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"60s", "1d", "3d"}, suggestionsBuilder)
                                )
                                .executes(context -> sprint(context.getSource(), IntegerArgumentType.getInteger(context, "time")))
                        )
                )
                .then(Commands.literal("unfreeze").executes(context -> setFreeze(context.getSource(), false)))
                .then(Commands.literal("freeze").executes(context -> setFreeze(context.getSource(), true)))
        );
    }

    private static String nanosToMilisString(long nanos) {
        return String.format("%.1f", (float)nanos / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND);
    }

    private static int setTickingRate(CommandSourceStack source, float rate) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        serverTickRateManager.setTickRate(rate);
        String string = String.format("%.1f", rate);
        source.sendSuccess(() -> Component.translatable("commands.tick.rate.success", string), true);
        return (int)rate;
    }

    private static int tickQuery(CommandSourceStack source) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        String string = nanosToMilisString(source.getServer().getAverageTickTimeNanos());
        float f = serverTickRateManager.tickrate();
        String string2 = String.format("%.1f", f);
        if (serverTickRateManager.isSprinting()) {
            source.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), false);
            source.sendSuccess(() -> Component.translatable("commands.tick.query.rate.sprinting", string2, string), false);
        } else {
            if (serverTickRateManager.isFrozen()) {
                source.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), false);
            } else if (serverTickRateManager.nanosecondsPerTick() < source.getServer().getAverageTickTimeNanos()) {
                source.sendSuccess(() -> Component.translatable("commands.tick.status.lagging"), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.tick.status.running"), false);
            }

            String string3 = nanosToMilisString(serverTickRateManager.nanosecondsPerTick());
            source.sendSuccess(() -> Component.translatable("commands.tick.query.rate.running", string2, string, string3), false);
        }

        long[] ls = Arrays.copyOf(source.getServer().getTickTimesNanos(), source.getServer().getTickTimesNanos().length);
        Arrays.sort(ls);
        String string4 = nanosToMilisString(ls[ls.length / 2]);
        String string5 = nanosToMilisString(ls[(int)((double)ls.length * 0.95)]);
        String string6 = nanosToMilisString(ls[(int)((double)ls.length * 0.99)]);
        source.sendSuccess(() -> Component.translatable("commands.tick.query.percentiles", string4, string5, string6, ls.length), false);
        return (int)f;
    }

    private static int sprint(CommandSourceStack source, int ticks) {
        boolean bl = source.getServer().tickRateManager().requestGameToSprint(ticks);
        if (bl) {
            source.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
        }

        source.sendSuccess(() -> Component.translatable("commands.tick.status.sprinting"), true);
        return 1;
    }

    private static int setFreeze(CommandSourceStack source, boolean frozen) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        if (frozen) {
            if (serverTickRateManager.isSprinting()) {
                serverTickRateManager.stopSprinting();
            }

            if (serverTickRateManager.isSteppingForward()) {
                serverTickRateManager.stopStepping();
            }
        }

        serverTickRateManager.setFrozen(frozen);
        if (frozen) {
            source.sendSuccess(() -> Component.translatable("commands.tick.status.frozen"), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.tick.status.running"), true);
        }

        return frozen ? 1 : 0;
    }

    private static int step(CommandSourceStack source, int steps) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        boolean bl = serverTickRateManager.stepGameIfPaused(steps);
        if (bl) {
            source.sendSuccess(() -> Component.translatable("commands.tick.step.success", steps), true);
        } else {
            source.sendFailure(Component.translatable("commands.tick.step.fail"));
        }

        return 1;
    }

    private static int stopStepping(CommandSourceStack source) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        boolean bl = serverTickRateManager.stopStepping();
        if (bl) {
            source.sendSuccess(() -> Component.translatable("commands.tick.step.stop.success"), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.tick.step.stop.fail"));
            return 0;
        }
    }

    private static int stopSprinting(CommandSourceStack source) {
        ServerTickRateManager serverTickRateManager = source.getServer().tickRateManager();
        boolean bl = serverTickRateManager.stopSprinting();
        if (bl) {
            source.sendSuccess(() -> Component.translatable("commands.tick.sprint.stop.success"), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.tick.sprint.stop.fail"));
            return 0;
        }
    }
}
