package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.FileUtil;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileZipper;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class PerfCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.perf.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(
        Component.translatable("commands.perf.alreadyRunning")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("perf")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("start").executes(context -> startProfilingDedicatedServer(context.getSource())))
                .then(Commands.literal("stop").executes(context -> stopProfilingDedicatedServer(context.getSource())))
        );
    }

    private static int startProfilingDedicatedServer(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftServer = source.getServer();
        if (minecraftServer.isRecordingMetrics()) {
            throw ERROR_ALREADY_RUNNING.create();
        } else {
            Consumer<ProfileResults> consumer = result -> whenStopped(source, result);
            Consumer<Path> consumer2 = dumpDirectory -> saveResults(source, dumpDirectory, minecraftServer);
            minecraftServer.startRecordingMetrics(consumer, consumer2);
            source.sendSuccess(() -> Component.translatable("commands.perf.started"), false);
            return 0;
        }
    }

    private static int stopProfilingDedicatedServer(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftServer = source.getServer();
        if (!minecraftServer.isRecordingMetrics()) {
            throw ERROR_NOT_RUNNING.create();
        } else {
            minecraftServer.finishRecordingMetrics();
            return 0;
        }
    }

    private static void saveResults(CommandSourceStack source, Path tempProfilingDirectory, MinecraftServer server) {
        String string = String.format(
            Locale.ROOT, "%s-%s-%s", Util.getFilenameFormattedDateTime(), server.getWorldData().getLevelName(), SharedConstants.getCurrentVersion().getId()
        );

        String string2;
        try {
            string2 = FileUtil.findAvailableName(MetricsPersister.PROFILING_RESULTS_DIR, string, ".zip");
        } catch (IOException var11) {
            source.sendFailure(Component.translatable("commands.perf.reportFailed"));
            LOGGER.error("Failed to create report name", (Throwable)var11);
            return;
        }

        try (FileZipper fileZipper = new FileZipper(MetricsPersister.PROFILING_RESULTS_DIR.resolve(string2))) {
            fileZipper.add(Paths.get("system.txt"), server.fillSystemReport(new SystemReport()).toLineSeparatedString());
            fileZipper.add(tempProfilingDirectory);
        }

        try {
            FileUtils.forceDelete(tempProfilingDirectory.toFile());
        } catch (IOException var9) {
            LOGGER.warn("Failed to delete temporary profiling file {}", tempProfilingDirectory, var9);
        }

        source.sendSuccess(() -> Component.translatable("commands.perf.reportSaved", string2), false);
    }

    private static void whenStopped(CommandSourceStack source, ProfileResults result) {
        if (result != EmptyProfileResults.EMPTY) {
            int i = result.getTickDuration();
            double d = (double)result.getNanoDuration() / (double)TimeUtil.NANOSECONDS_PER_SECOND;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.perf.stopped", String.format(Locale.ROOT, "%.2f", d), i, String.format(Locale.ROOT, "%.2f", (double)i / d)
                    ),
                false
            );
        }
    }
}
