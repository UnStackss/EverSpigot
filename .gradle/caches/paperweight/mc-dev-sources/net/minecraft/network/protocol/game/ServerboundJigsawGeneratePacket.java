package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundJigsawGeneratePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundJigsawGeneratePacket> STREAM_CODEC = Packet.codec(
        ServerboundJigsawGeneratePacket::write, ServerboundJigsawGeneratePacket::new
    );
    private final BlockPos pos;
    private final int levels;
    private final boolean keepJigsaws;

    public ServerboundJigsawGeneratePacket(BlockPos pos, int maxDepth, boolean keepJigsaws) {
        this.pos = pos;
        this.levels = maxDepth;
        this.keepJigsaws = keepJigsaws;
    }

    private ServerboundJigsawGeneratePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.levels = buf.readVarInt();
        this.keepJigsaws = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.levels);
        buf.writeBoolean(this.keepJigsaws);
    }

    @Override
    public PacketType<ServerboundJigsawGeneratePacket> type() {
        return GamePacketTypes.SERVERBOUND_JIGSAW_GENERATE;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleJigsawGenerate(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int levels() {
        return this.levels;
    }

    public boolean keepJigsaws() {
        return this.keepJigsaws;
    }
}
