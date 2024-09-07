package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChunkBatchReceivedPacket(float desiredChunksPerTick) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChunkBatchReceivedPacket> STREAM_CODEC = Packet.codec(
        ServerboundChunkBatchReceivedPacket::write, ServerboundChunkBatchReceivedPacket::new
    );

    private ServerboundChunkBatchReceivedPacket(FriendlyByteBuf buf) {
        this(buf.readFloat());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.desiredChunksPerTick);
    }

    @Override
    public PacketType<ServerboundChunkBatchReceivedPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHUNK_BATCH_RECEIVED;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleChunkBatchReceived(this);
    }
}
