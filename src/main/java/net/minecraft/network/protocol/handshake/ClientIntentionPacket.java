// mc-dev import
package net.minecraft.network.protocol.handshake;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientIntentionPacket(int protocolVersion, String hostName, int port, ClientIntent intention) implements Packet<ServerHandshakePacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ClientIntentionPacket> STREAM_CODEC = Packet.codec(ClientIntentionPacket::write, ClientIntentionPacket::new);
    private static final int MAX_HOST_LENGTH = 255;

    private ClientIntentionPacket(FriendlyByteBuf buf) {
        // Spigot - increase max hostName length
        this(buf.readVarInt(), buf.readUtf(Short.MAX_VALUE), buf.readUnsignedShort(), ClientIntent.byId(buf.readVarInt()));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.protocolVersion);
        buf.writeUtf(this.hostName);
        buf.writeShort(this.port);
        buf.writeVarInt(this.intention.id());
    }

    @Override
    public PacketType<ClientIntentionPacket> type() {
        return HandshakePacketTypes.CLIENT_INTENTION;
    }

    public void handle(ServerHandshakePacketListener listener) {
        listener.handleIntention(this);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
