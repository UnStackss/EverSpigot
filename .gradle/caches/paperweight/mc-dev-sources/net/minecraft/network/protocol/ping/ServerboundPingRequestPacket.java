package net.minecraft.network.protocol.ping;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPingRequestPacket implements Packet<ServerPingPacketListener> {
    public static final StreamCodec<ByteBuf, ServerboundPingRequestPacket> STREAM_CODEC = Packet.codec(
        ServerboundPingRequestPacket::write, ServerboundPingRequestPacket::new
    );
    private final long time;

    public ServerboundPingRequestPacket(long startTime) {
        this.time = startTime;
    }

    private ServerboundPingRequestPacket(ByteBuf buf) {
        this.time = buf.readLong();
    }

    private void write(ByteBuf buf) {
        buf.writeLong(this.time);
    }

    @Override
    public PacketType<ServerboundPingRequestPacket> type() {
        return PingPacketTypes.SERVERBOUND_PING_REQUEST;
    }

    @Override
    public void handle(ServerPingPacketListener listener) {
        listener.handlePingRequest(this);
    }

    public long getTime() {
        return this.time;
    }
}
