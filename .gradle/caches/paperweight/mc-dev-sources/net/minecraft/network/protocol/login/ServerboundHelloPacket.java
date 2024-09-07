package net.minecraft.network.protocol.login;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundHelloPacket(String name, UUID profileId) implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundHelloPacket> STREAM_CODEC = Packet.codec(
        ServerboundHelloPacket::write, ServerboundHelloPacket::new
    );

    private ServerboundHelloPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(16), buf.readUUID());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.name, 16);
        buf.writeUUID(this.profileId);
    }

    @Override
    public PacketType<ServerboundHelloPacket> type() {
        return LoginPacketTypes.SERVERBOUND_HELLO;
    }

    @Override
    public void handle(ServerLoginPacketListener listener) {
        listener.handleHello(this);
    }
}
