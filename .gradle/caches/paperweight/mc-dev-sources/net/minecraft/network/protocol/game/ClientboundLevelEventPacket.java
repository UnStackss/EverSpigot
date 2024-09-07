package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundLevelEventPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLevelEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundLevelEventPacket::write, ClientboundLevelEventPacket::new
    );
    private final int type;
    private final BlockPos pos;
    private final int data;
    private final boolean globalEvent;

    public ClientboundLevelEventPacket(int eventId, BlockPos pos, int data, boolean global) {
        this.type = eventId;
        this.pos = pos.immutable();
        this.data = data;
        this.globalEvent = global;
    }

    private ClientboundLevelEventPacket(FriendlyByteBuf buf) {
        this.type = buf.readInt();
        this.pos = buf.readBlockPos();
        this.data = buf.readInt();
        this.globalEvent = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.type);
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.data);
        buf.writeBoolean(this.globalEvent);
    }

    @Override
    public PacketType<ClientboundLevelEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LEVEL_EVENT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleLevelEvent(this);
    }

    public boolean isGlobalEvent() {
        return this.globalEvent;
    }

    public int getType() {
        return this.type;
    }

    public int getData() {
        return this.data;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
