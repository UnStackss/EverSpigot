package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkWithLightPacket> STREAM_CODEC = Packet.codec(
        ClientboundLevelChunkWithLightPacket::write, ClientboundLevelChunkWithLightPacket::new
    );
    private final int x;
    private final int z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLevelChunkWithLightPacket(LevelChunk chunk, LevelLightEngine lightProvider, @Nullable BitSet skyBits, @Nullable BitSet blockBits) {
        ChunkPos chunkPos = chunk.getPos();
        this.x = chunkPos.x;
        this.z = chunkPos.z;
        this.chunkData = new ClientboundLevelChunkPacketData(chunk);
        this.lightData = new ClientboundLightUpdatePacketData(chunkPos, lightProvider, skyBits, blockBits);
    }

    private ClientboundLevelChunkWithLightPacket(RegistryFriendlyByteBuf buf) {
        this.x = buf.readInt();
        this.z = buf.readInt();
        this.chunkData = new ClientboundLevelChunkPacketData(buf, this.x, this.z);
        this.lightData = new ClientboundLightUpdatePacketData(buf, this.x, this.z);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.z);
        this.chunkData.write(buf);
        this.lightData.write(buf);
    }

    @Override
    public PacketType<ClientboundLevelChunkWithLightPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LEVEL_CHUNK_WITH_LIGHT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleLevelChunkWithLight(this);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public ClientboundLevelChunkPacketData getChunkData() {
        return this.chunkData;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
