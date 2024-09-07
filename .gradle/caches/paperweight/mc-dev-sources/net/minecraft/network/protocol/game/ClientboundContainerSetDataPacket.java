package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundContainerSetDataPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundContainerSetDataPacket> STREAM_CODEC = Packet.codec(
        ClientboundContainerSetDataPacket::write, ClientboundContainerSetDataPacket::new
    );
    private final int containerId;
    private final int id;
    private final int value;

    public ClientboundContainerSetDataPacket(int syncId, int propertyId, int value) {
        this.containerId = syncId;
        this.id = propertyId;
        this.value = value;
    }

    private ClientboundContainerSetDataPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readUnsignedByte();
        this.id = buf.readShort();
        this.value = buf.readShort();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeShort(this.id);
        buf.writeShort(this.value);
    }

    @Override
    public PacketType<ClientboundContainerSetDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CONTAINER_SET_DATA;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleContainerSetData(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public int getId() {
        return this.id;
    }

    public int getValue() {
        return this.value;
    }
}
