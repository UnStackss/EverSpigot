package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPickItemPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPickItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundPickItemPacket::write, ServerboundPickItemPacket::new
    );
    private final int slot;

    public ServerboundPickItemPacket(int slot) {
        this.slot = slot;
    }

    private ServerboundPickItemPacket(FriendlyByteBuf buf) {
        this.slot = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.slot);
    }

    @Override
    public PacketType<ServerboundPickItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_PICK_ITEM;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handlePickItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
