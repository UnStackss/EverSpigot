package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetCarriedItemPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetCarriedItemPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetCarriedItemPacket::write, ClientboundSetCarriedItemPacket::new
    );
    private final int slot;

    public ClientboundSetCarriedItemPacket(int slot) {
        this.slot = slot;
    }

    private ClientboundSetCarriedItemPacket(FriendlyByteBuf buf) {
        this.slot = buf.readByte();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(this.slot);
    }

    @Override
    public PacketType<ClientboundSetCarriedItemPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_CARRIED_ITEM;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetCarriedItem(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
