package net.minecraft.server.commands;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.chase.ChaseClient;
import net.minecraft.server.chase.ChaseServer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class ChaseCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_CONNECT_HOST = "localhost";
    private static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
    private static final int DEFAULT_PORT = 10000;
    private static final int BROADCAST_INTERVAL_MS = 100;
    public static BiMap<String, ResourceKey<Level>> DIMENSION_NAMES = ImmutableBiMap.of("o", Level.OVERWORLD, "n", Level.NETHER, "e", Level.END);
    @Nullable
    private static ChaseServer chaseServer;
    @Nullable
    private static ChaseClient chaseClient;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("chase")
                .then(
                    Commands.literal("follow")
                        .then(
                            Commands.argument("host", StringArgumentType.string())
                                .executes(context -> follow(context.getSource(), StringArgumentType.getString(context, "host"), 10000))
                                .then(
                                    Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                        .executes(
                                            context -> follow(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "host"),
                                                    IntegerArgumentType.getInteger(context, "port")
                                                )
                                        )
                                )
                        )
                        .executes(context -> follow(context.getSource(), "localhost", 10000))
                )
                .then(
                    Commands.literal("lead")
                        .then(
                            Commands.argument("bind_address", StringArgumentType.string())
                                .executes(context -> lead(context.getSource(), StringArgumentType.getString(context, "bind_address"), 10000))
                                .then(
                                    Commands.argument("port", IntegerArgumentType.integer(1024, 65535))
                                        .executes(
                                            context -> lead(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "bind_address"),
                                                    IntegerArgumentType.getInteger(context, "port")
                                                )
                                        )
                                )
                        )
                        .executes(context -> lead(context.getSource(), "0.0.0.0", 10000))
                )
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
        );
    }

    private static int stop(CommandSourceStack source) {
        if (chaseClient != null) {
            chaseClient.stop();
            source.sendSuccess(() -> Component.literal("You have now stopped chasing"), false);
            chaseClient = null;
        }

        if (chaseServer != null) {
            chaseServer.stop();
            source.sendSuccess(() -> Component.literal("You are no longer being chased"), false);
            chaseServer = null;
        }

        return 0;
    }

    private static boolean alreadyRunning(CommandSourceStack source) {
        if (chaseServer != null) {
            source.sendFailure(Component.literal("Chase server is already running. Stop it using /chase stop"));
            return true;
        } else if (chaseClient != null) {
            source.sendFailure(Component.literal("You are already chasing someone. Stop it using /chase stop"));
            return true;
        } else {
            return false;
        }
    }

    private static int lead(CommandSourceStack source, String ip, int port) {
        if (alreadyRunning(source)) {
            return 0;
        } else {
            chaseServer = new ChaseServer(ip, port, source.getServer().getPlayerList(), 100);

            try {
                chaseServer.start();
                source.sendSuccess(
                    () -> Component.literal("Chase server is now running on port " + port + ". Clients can follow you using /chase follow <ip> <port>"), false
                );
            } catch (IOException var4) {
                LOGGER.error("Failed to start chase server", (Throwable)var4);
                source.sendFailure(Component.literal("Failed to start chase server on port " + port));
                chaseServer = null;
            }

            return 0;
        }
    }

    private static int follow(CommandSourceStack source, String ip, int port) {
        if (alreadyRunning(source)) {
            return 0;
        } else {
            chaseClient = new ChaseClient(ip, port, source.getServer());
            chaseClient.start();
            source.sendSuccess(
                () -> Component.literal(
                        "You are now chasing "
                            + ip
                            + ":"
                            + port
                            + ". If that server does '/chase lead' then you will automatically go to the same position. Use '/chase stop' to stop chasing."
                    ),
                false
            );
            return 0;
        }
    }
}
