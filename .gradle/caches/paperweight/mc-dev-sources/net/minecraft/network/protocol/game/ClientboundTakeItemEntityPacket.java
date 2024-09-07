package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundTakeItemEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTakeItemEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundTakeItemEntityPacket::write, ClientboundTakeItemEntityPacket::new
    );
    private final int itemId;
    private final int playerId;
    private final int amount;

    public ClientboundTakeItemEntityPacket(int entityId, int collectorId, int stackAmount) {
        this.itemId = entityId;
        this.playerId = collectorId;
        this.amount = stackAmount;
    }

    private ClientboundTakeItemEntityPacket(FriendlyByteBuf buf) {
        this.itemId = buf.readVarInt();
        this.playerId = buf.readVarInt();
        this.amount = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.itemId);
        buf.writeVarInt(this.playerId);
        buf.writeVarInt(this.amount);
    }

    @Override
    public PacketType<ClientboundTakeItemEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_TAKE_ITEM_ENTITY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleTakeItemEntity(this);
    }

    public int getItemId() {
        return this.itemId;
    }

    public int getPlayerId() {
        return this.playerId;
    }

    public int getAmount() {
        return this.amount;
    }
}
