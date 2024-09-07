package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundSelectTradePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSelectTradePacket> STREAM_CODEC = Packet.codec(
        ServerboundSelectTradePacket::write, ServerboundSelectTradePacket::new
    );
    private final int item;

    public ServerboundSelectTradePacket(int tradeId) {
        this.item = tradeId;
    }

    private ServerboundSelectTradePacket(FriendlyByteBuf buf) {
        this.item = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.item);
    }

    @Override
    public PacketType<ServerboundSelectTradePacket> type() {
        return GamePacketTypes.SERVERBOUND_SELECT_TRADE;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleSelectTrade(this);
    }

    public int getItem() {
        return this.item;
    }
}
