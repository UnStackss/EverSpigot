package net.minecraft.network.protocol.common;

import net.minecraft.network.protocol.cookie.ServerCookiePacketListener;
import net.minecraft.network.protocol.game.ServerPacketListener;

public interface ServerCommonPacketListener extends ServerCookiePacketListener, ServerPacketListener {
    void handleKeepAlive(ServerboundKeepAlivePacket packet);

    void handlePong(ServerboundPongPacket packet);

    void handleCustomPayload(ServerboundCustomPayloadPacket packet);

    void handleResourcePackResponse(ServerboundResourcePackPacket packet);

    void handleClientInformation(ServerboundClientInformationPacket packet);
}
