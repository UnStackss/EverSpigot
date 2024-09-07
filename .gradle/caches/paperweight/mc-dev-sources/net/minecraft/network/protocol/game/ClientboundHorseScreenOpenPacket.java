package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundHorseScreenOpenPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundHorseScreenOpenPacket> STREAM_CODEC = Packet.codec(
        ClientboundHorseScreenOpenPacket::write, ClientboundHorseScreenOpenPacket::new
    );
    private final int containerId;
    private final int inventoryColumns;
    private final int entityId;

    public ClientboundHorseScreenOpenPacket(int syncId, int slotColumnCount, int horseId) {
        this.containerId = syncId;
        this.inventoryColumns = slotColumnCount;
        this.entityId = horseId;
    }

    private ClientboundHorseScreenOpenPacket(FriendlyByteBuf buf) {
        this.containerId = buf.readUnsignedByte();
        this.inventoryColumns = buf.readVarInt();
        this.entityId = buf.readInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeVarInt(this.inventoryColumns);
        buf.writeInt(this.entityId);
    }

    @Override
    public PacketType<ClientboundHorseScreenOpenPacket> type() {
        return GamePacketTypes.CLIENTBOUND_HORSE_SCREEN_OPEN;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleHorseScreenOpen(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public int getInventoryColumns() {
        return this.inventoryColumns;
    }

    public int getEntityId() {
        return this.entityId;
    }
}
