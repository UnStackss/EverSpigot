package net.minecraft.network.protocol.cookie;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public record ClientboundCookieRequestPacket(ResourceLocation key) implements Packet<ClientCookiePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCookieRequestPacket> STREAM_CODEC = Packet.codec(
        ClientboundCookieRequestPacket::write, ClientboundCookieRequestPacket::new
    );

    private ClientboundCookieRequestPacket(FriendlyByteBuf buf) {
        this(buf.readResourceLocation());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.key);
    }

    @Override
    public PacketType<ClientboundCookieRequestPacket> type() {
        return CookiePacketTypes.CLIENTBOUND_COOKIE_REQUEST;
    }

    @Override
    public void handle(ClientCookiePacketListener listener) {
        listener.handleRequestCookie(this);
    }
}
