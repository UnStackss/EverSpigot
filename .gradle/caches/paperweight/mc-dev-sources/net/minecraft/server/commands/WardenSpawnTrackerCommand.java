package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.Player;

public class WardenSpawnTrackerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("warden_spawn_tracker")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("clear")
                        .executes(context -> resetTracker(context.getSource(), ImmutableList.of(context.getSource().getPlayerOrException())))
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("warning_level", IntegerArgumentType.integer(0, 4))
                                .executes(
                                    context -> setWarningLevel(
                                            context.getSource(),
                                            ImmutableList.of(context.getSource().getPlayerOrException()),
                                            IntegerArgumentType.getInteger(context, "warning_level")
                                        )
                                )
                        )
                )
        );
    }

    private static int setWarningLevel(CommandSourceStack source, Collection<? extends Player> players, int warningCount) {
        for (Player player : players) {
            player.getWardenSpawnTracker().ifPresent(warningManager -> warningManager.setWarningLevel(warningCount));
        }

        if (players.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.warden_spawn_tracker.set.success.single", players.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.warden_spawn_tracker.set.success.multiple", players.size()), true);
        }

        return players.size();
    }

    private static int resetTracker(CommandSourceStack source, Collection<? extends Player> players) {
        for (Player player : players) {
            player.getWardenSpawnTracker().ifPresent(WardenSpawnTracker::reset);
        }

        if (players.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.warden_spawn_tracker.clear.success.single", players.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.warden_spawn_tracker.clear.success.multiple", players.size()), true);
        }

        return players.size();
    }
}
