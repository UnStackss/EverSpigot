package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.level.border.WorldBorder;

public class ClientboundSetBorderWarningDelayPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetBorderWarningDelayPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetBorderWarningDelayPacket::write, ClientboundSetBorderWarningDelayPacket::new
    );
    private final int warningDelay;

    public ClientboundSetBorderWarningDelayPacket(WorldBorder worldBorder) {
        this.warningDelay = worldBorder.getWarningTime();
    }

    private ClientboundSetBorderWarningDelayPacket(FriendlyByteBuf buf) {
        this.warningDelay = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.warningDelay);
    }

    @Override
    public PacketType<ClientboundSetBorderWarningDelayPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_BORDER_WARNING_DELAY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetBorderWarningDelay(this);
    }

    public int getWarningDelay() {
        return this.warningDelay;
    }
}
