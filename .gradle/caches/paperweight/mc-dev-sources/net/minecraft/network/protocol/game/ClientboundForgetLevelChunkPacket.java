package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;

public record ClientboundForgetLevelChunkPacket(ChunkPos pos) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundForgetLevelChunkPacket> STREAM_CODEC = Packet.codec(
        ClientboundForgetLevelChunkPacket::write, ClientboundForgetLevelChunkPacket::new
    );

    private ClientboundForgetLevelChunkPacket(FriendlyByteBuf buf) {
        this(buf.readChunkPos());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeChunkPos(this.pos);
    }

    @Override
    public PacketType<ClientboundForgetLevelChunkPacket> type() {
        return GamePacketTypes.CLIENTBOUND_FORGET_LEVEL_CHUNK;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleForgetLevelChunk(this);
    }
}
