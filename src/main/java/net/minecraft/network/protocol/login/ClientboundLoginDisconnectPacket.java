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
        this.reason = Component.Serializer.fromJsonLenient(buf.readUtf(FriendlyByteBuf.MAX_COMPONENT_STRING_LENGTH), RegistryAccess.EMPTY); // Paper - diff on change
    }

    private void write(FriendlyByteBuf buf) {
        // Paper start - Adventure
        // buf.writeUtf(Component.Serializer.toJson(this.reason, RegistryAccess.EMPTY));
        // In the login phase, buf.adventure$locale field is most likely null, but plugins may use internals to set it via the channel attribute
        java.util.Locale bufLocale = buf.adventure$locale;
        buf.writeJsonWithCodec(net.minecraft.network.chat.ComponentSerialization.localizedCodec(bufLocale == null ? java.util.Locale.US : bufLocale), this.reason, FriendlyByteBuf.MAX_COMPONENT_STRING_LENGTH);
        // Paper end - Adventure
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
