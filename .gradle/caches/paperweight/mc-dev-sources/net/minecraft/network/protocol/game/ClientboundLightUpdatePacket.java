package net.minecraft.network.protocol.game;

import java.util.BitSet;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLightUpdatePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLightUpdatePacket> STREAM_CODEC = Packet.codec(
        ClientboundLightUpdatePacket::write, ClientboundLightUpdatePacket::new
    );
    private final int x;
    private final int z;
    private final ClientboundLightUpdatePacketData lightData;

    public ClientboundLightUpdatePacket(ChunkPos chunkPos, LevelLightEngine lightProvider, @Nullable BitSet skyBits, @Nullable BitSet blockBits) {
        this.x = chunkPos.x;
        this.z = chunkPos.z;
        this.lightData = new ClientboundLightUpdatePacketData(chunkPos, lightProvider, skyBits, blockBits);
    }

    private ClientboundLightUpdatePacket(FriendlyByteBuf buf) {
        this.x = buf.readVarInt();
        this.z = buf.readVarInt();
        this.lightData = new ClientboundLightUpdatePacketData(buf, this.x, this.z);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.x);
        buf.writeVarInt(this.z);
        this.lightData.write(buf);
    }

    @Override
    public PacketType<ClientboundLightUpdatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_LIGHT_UPDATE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleLightUpdatePacket(this);
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public ClientboundLightUpdatePacketData getLightData() {
        return this.lightData;
    }
}
