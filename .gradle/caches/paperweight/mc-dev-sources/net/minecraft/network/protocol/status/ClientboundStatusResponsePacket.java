package net.minecraft.network.protocol.status;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundStatusResponsePacket(ServerStatus status) implements Packet<ClientStatusPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundStatusResponsePacket> STREAM_CODEC = Packet.codec(
        ClientboundStatusResponsePacket::write, ClientboundStatusResponsePacket::new
    );

    private ClientboundStatusResponsePacket(FriendlyByteBuf buf) {
        this(buf.readJsonWithCodec(ServerStatus.CODEC));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeJsonWithCodec(ServerStatus.CODEC, this.status);
    }

    @Override
    public PacketType<ClientboundStatusResponsePacket> type() {
        return StatusPacketTypes.CLIENTBOUND_STATUS_RESPONSE;
    }

    @Override
    public void handle(ClientStatusPacketListener listener) {
        listener.handleStatusResponse(this);
    }
}
