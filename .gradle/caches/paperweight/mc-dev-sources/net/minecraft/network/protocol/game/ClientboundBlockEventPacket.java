package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.block.Block;

public class ClientboundBlockEventPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBlockEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundBlockEventPacket::write, ClientboundBlockEventPacket::new
    );
    private final BlockPos pos;
    private final int b0;
    private final int b1;
    private final Block block;

    public ClientboundBlockEventPacket(BlockPos pos, Block block, int type, int data) {
        this.pos = pos;
        this.block = block;
        this.b0 = type;
        this.b1 = data;
    }

    private ClientboundBlockEventPacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.b0 = buf.readUnsignedByte();
        this.b1 = buf.readUnsignedByte();
        this.block = ByteBufCodecs.registry(Registries.BLOCK).decode(buf);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.b0);
        buf.writeByte(this.b1);
        ByteBufCodecs.registry(Registries.BLOCK).encode(buf, this.block);
    }

    @Override
    public PacketType<ClientboundBlockEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_BLOCK_EVENT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleBlockEvent(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getB0() {
        return this.b0;
    }

    public int getB1() {
        return this.b1;
    }

    public Block getBlock() {
        return this.block;
    }
}
