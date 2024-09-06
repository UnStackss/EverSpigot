package net.minecraft.network.protocol.login;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundLoginDisconnectPacket implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLoginDisconnectPacket> STREAM_CODEC = Packet.codec(
        ClientboundLoginDisconnectPacket::write, ClientboundLoginDisconnectPacket::new
    );
    private final Component reason;

    public ClientboundLoginDisconnectPacket(Component reason) {
        this.reason = reason;
    }

    private ClientboundLoginDisconnectPacket(FriendlyByteBuf buf) {
        this.reason = Component.Serializer.fromJsonLenient(buf.readUtf(262144), RegistryAccess.EMPTY);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(Component.Serializer.toJson(this.reason, RegistryAccess.EMPTY));
    }

    @Override
    public PacketType<ClientboundLoginDisconnectPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_LOGIN_DISCONNECT;
    }

    @Override
    public void handle(ClientLoginPacketListener listener) {
        listener.handleDisconnect(this);
    }

    public Component getReason() {
        return this.reason;
    }
}
