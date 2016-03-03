package net.minecraft.network.protocol.game;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public record ClientboundChunksBiomesPacket(List<ClientboundChunksBiomesPacket.ChunkBiomeData> chunkBiomeData) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundChunksBiomesPacket> STREAM_CODEC = Packet.codec(
        ClientboundChunksBiomesPacket::write, ClientboundChunksBiomesPacket::new
    );
    private static final int TWO_MEGABYTES = 2097152;

    private ClientboundChunksBiomesPacket(FriendlyByteBuf buf) {
        this(buf.readList(ClientboundChunksBiomesPacket.ChunkBiomeData::new));
    }

    public static ClientboundChunksBiomesPacket forChunks(List<LevelChunk> chunks) {
        return new ClientboundChunksBiomesPacket(chunks.stream().map(ClientboundChunksBiomesPacket.ChunkBiomeData::new).toList());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeCollection(this.chunkBiomeData, (bufx, data) -> data.write(bufx));
    }

    @Override
    public PacketType<ClientboundChunksBiomesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CHUNKS_BIOMES;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleChunksBiomes(this);
    }

    public static record ChunkBiomeData(ChunkPos pos, byte[] buffer) {
        public ChunkBiomeData(LevelChunk chunk) {
            this(chunk.getPos(), new byte[calculateChunkSize(chunk)]);
            extractChunkData(new FriendlyByteBuf(this.getWriteBuffer()), chunk);
        }

        public ChunkBiomeData(FriendlyByteBuf buf) {
            this(buf.readChunkPos(), buf.readByteArray(2097152));
        }

        private static int calculateChunkSize(LevelChunk chunk) {
            int i = 0;

            for (LevelChunkSection levelChunkSection : chunk.getSections()) {
                i += levelChunkSection.getBiomes().getSerializedSize();
            }

            return i;
        }

        public FriendlyByteBuf getReadBuffer() {
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.buffer));
        }

        private ByteBuf getWriteBuffer() {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(this.buffer);
            byteBuf.writerIndex(0);
            return byteBuf;
        }

        public static void extractChunkData(FriendlyByteBuf buf, LevelChunk chunk) {
            for (LevelChunkSection levelChunkSection : chunk.getSections()) {
                levelChunkSection.getBiomes().write(buf);
            }
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeChunkPos(this.pos);
            buf.writeByteArray(this.buffer);
        }
    }
}