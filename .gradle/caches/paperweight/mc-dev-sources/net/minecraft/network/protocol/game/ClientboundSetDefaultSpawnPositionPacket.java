package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetDefaultSpawnPositionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetDefaultSpawnPositionPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetDefaultSpawnPositionPacket::write, ClientboundSetDefaultSpawnPositionPacket::new
    );
    public final BlockPos pos;
    private final float angle;

    public ClientboundSetDefaultSpawnPositionPacket(BlockPos pos, float angle) {
        this.pos = pos;
        this.angle = angle;
    }

    private ClientboundSetDefaultSpawnPositionPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.angle = buf.readFloat();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeFloat(this.angle);
    }

    @Override
    public PacketType<ClientboundSetDefaultSpawnPositionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_DEFAULT_SPAWN_POSITION;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetSpawn(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public float getAngle() {
        return this.angle;
    }
}
