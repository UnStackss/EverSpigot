package net.minecraft.network.protocol.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ClientInformation;

public record ServerboundClientInformationPacket(ClientInformation information) implements Packet<ServerCommonPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundClientInformationPacket> STREAM_CODEC = Packet.codec(
        ServerboundClientInformationPacket::write, ServerboundClientInformationPacket::new
    );

    private ServerboundClientInformationPacket(FriendlyByteBuf buf) {
        this(new ClientInformation(buf));
    }

    private void write(FriendlyByteBuf buf) {
        this.information.write(buf);
    }

    @Override
    public PacketType<ServerboundClientInformationPacket> type() {
        return CommonPacketTypes.SERVERBOUND_CLIENT_INFORMATION;
    }

    @Override
    public void handle(ServerCommonPacketListener listener) {
        listener.handleClientInformation(this);
    }
}
