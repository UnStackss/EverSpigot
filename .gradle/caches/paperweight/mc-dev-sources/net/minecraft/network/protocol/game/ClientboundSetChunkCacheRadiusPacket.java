package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetChunkCacheRadiusPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetChunkCacheRadiusPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetChunkCacheRadiusPacket::write, ClientboundSetChunkCacheRadiusPacket::new
    );
    private final int radius;

    public ClientboundSetChunkCacheRadiusPacket(int distance) {
        this.radius = distance;
    }

    private ClientboundSetChunkCacheRadiusPacket(FriendlyByteBuf buf) {
        this.radius = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.radius);
    }

    @Override
    public PacketType<ClientboundSetChunkCacheRadiusPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_CHUNK_CACHE_RADIUS;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetChunkCacheRadius(this);
    }

    public int getRadius() {
        return this.radius;
    }
}
