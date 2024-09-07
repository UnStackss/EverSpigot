package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundSetCarriedItemPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetCarriedItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetCarriedItemPacket::write, ServerboundSetCarriedItemPacket::new
    );
    private final int slot;

    public ServerboundSetCarriedItemPacket(int selectedSlot) {
        this.slot = selectedSlot;
    }

    private ServerboundSetCarriedItemPacket(FriendlyByteBuf buf) {
        this.slot = buf.readShort();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeShort(this.slot);
    }

    @Override
    public PacketType<ServerboundSetCarriedItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_CARRIED_ITEM;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleSetCarriedItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
