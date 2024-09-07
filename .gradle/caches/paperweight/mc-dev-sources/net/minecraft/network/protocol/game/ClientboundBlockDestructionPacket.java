package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundBlockDestructionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundBlockDestructionPacket> STREAM_CODEC = Packet.codec(
        ClientboundBlockDestructionPacket::write, ClientboundBlockDestructionPacket::new
    );
    private final int id;
    private final BlockPos pos;
    private final int progress;

    public ClientboundBlockDestructionPacket(int entityId, BlockPos pos, int progress) {
        this.id = entityId;
        this.pos = pos;
        this.progress = progress;
    }

    private ClientboundBlockDestructionPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.pos = buf.readBlockPos();
        this.progress = buf.readUnsignedByte();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.progress);
    }

    @Override
    public PacketType<ClientboundBlockDestructionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BLOCK_DESTRUCTION;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleBlockDestruction(this);
    }

    public int getId() {
        return this.id;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getProgress() {
        return this.progress;
    }
}
