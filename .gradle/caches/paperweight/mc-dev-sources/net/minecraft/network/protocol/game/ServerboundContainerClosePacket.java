package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundContainerClosePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundContainerClosePacket> STREAM_CODEC = Packet.codec(
        ServerboundContainerClosePacket::write, ServerboundContainerClosePacket::new
    );
    private final int containerId;

    public ServerboundContainerClosePacket(int syncId) {
        this.containerId = syncId;
    }

    private ServerboundContainerClosePacket(FriendlyByteBuf buf) {
        this.containerId = buf.readByte();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
    }

    @Override
    public PacketType<ServerboundContainerClosePacket> type() {
        return GamePacketTypes.SERVERBOUND_CONTAINER_CLOSE;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleContainerClose(this);
    }

    public int getContainerId() {
        return this.containerId;
    }
}
