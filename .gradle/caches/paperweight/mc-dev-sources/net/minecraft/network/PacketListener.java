package net.minecraft.network;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketUtils;

public interface PacketListener {
    PacketFlow flow();

    ConnectionProtocol protocol();

    void onDisconnect(DisconnectionDetails info);

    default void onPacketError(Packet packet, Exception exception) throws ReportedException {
        throw PacketUtils.makeReportedException(exception, packet, this);
    }

    default DisconnectionDetails createDisconnectionInfo(Component reason, Throwable exception) {
        return new DisconnectionDetails(reason);
    }

    boolean isAcceptingMessages();

    default boolean shouldHandleMessage(Packet<?> packet) {
        return this.isAcceptingMessages();
    }

    default void fillCrashReport(CrashReport report) {
        CrashReportCategory crashReportCategory = report.addCategory("Connection");
        crashReportCategory.setDetail("Protocol", () -> this.protocol().id());
        crashReportCategory.setDetail("Flow", () -> this.flow().toString());
        this.fillListenerSpecificCrashDetails(report, crashReportCategory);
    }

    default void fillListenerSpecificCrashDetails(CrashReport report, CrashReportCategory section) {
    }
}
