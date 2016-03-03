package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
// CraftBukkit end
import net.minecraft.util.thread.BlockableEventLoop;

public class PacketUtils {

    private static final Logger LOGGER = LogUtils.getLogger();

    public PacketUtils() {}

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T listener, ServerLevel world) throws RunningOnDifferentThreadException {
        PacketUtils.ensureRunningOnSameThread(packet, listener, (BlockableEventLoop) world.getServer());
    }

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T listener, BlockableEventLoop<?> engine) throws RunningOnDifferentThreadException {
        if (!engine.isSameThread()) {
            engine.executeIfPossible(() -> {
                if (listener instanceof ServerCommonPacketListenerImpl serverCommonPacketListener && serverCommonPacketListener.processedDisconnect) return; // CraftBukkit - Don't handle sync packets for kicked players
                if (listener.shouldHandleMessage(packet)) {
                    co.aikar.timings.Timing timing = co.aikar.timings.MinecraftTimings.getPacketTiming(packet); // Paper - timings
                    try (co.aikar.timings.Timing ignored = timing.startTiming()) { // Paper - timings
                        packet.handle(listener);
                    } catch (Exception exception) {
                        if (exception instanceof ReportedException) {
                            ReportedException reportedexception = (ReportedException) exception;

                            if (reportedexception.getCause() instanceof OutOfMemoryError) {
                                throw PacketUtils.makeReportedException(exception, packet, listener);
                            }
                        }

                        listener.onPacketError(packet, exception);
                    }
                } else {
                    PacketUtils.LOGGER.debug("Ignoring packet due to disconnection: {}", packet);
                }

            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    public static <T extends PacketListener> ReportedException makeReportedException(Exception exception, Packet<T> packet, T listener) {
        if (exception instanceof ReportedException reportedexception) {
            PacketUtils.fillCrashReport(reportedexception.getReport(), listener, packet);
            return reportedexception;
        } else {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Main thread packet handler");

            PacketUtils.fillCrashReport(crashreport, listener, packet);
            return new ReportedException(crashreport);
        }
    }

    public static <T extends PacketListener> void fillCrashReport(CrashReport report, T listener, @Nullable Packet<T> packet) {
        if (packet != null) {
            CrashReportCategory crashreportsystemdetails = report.addCategory("Incoming Packet");

            crashreportsystemdetails.setDetail("Type", () -> {
                return packet.type().toString();
            });
            crashreportsystemdetails.setDetail("Is Terminal", () -> {
                return Boolean.toString(packet.isTerminal());
            });
            crashreportsystemdetails.setDetail("Is Skippable", () -> {
                return Boolean.toString(packet.isSkippable());
            });
        }

        listener.fillCrashReport(report);
    }
}
