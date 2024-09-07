package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundClearTitlesPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundClearTitlesPacket> STREAM_CODEC = Packet.codec(
        ClientboundClearTitlesPacket::write, ClientboundClearTitlesPacket::new
    );
    private final boolean resetTimes;

    public ClientboundClearTitlesPacket(boolean reset) {
        this.resetTimes = reset;
    }

    private ClientboundClearTitlesPacket(FriendlyByteBuf buf) {
        this.resetTimes = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.resetTimes);
    }

    @Override
    public PacketType<ClientboundClearTitlesPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CLEAR_TITLES;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleTitlesClear(this);
    }

    public boolean shouldResetTimes() {
        return this.resetTimes;
    }
}
