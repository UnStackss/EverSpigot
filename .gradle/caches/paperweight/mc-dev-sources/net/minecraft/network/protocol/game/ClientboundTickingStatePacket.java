package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.TickRateManager;

public record ClientboundTickingStatePacket(float tickRate, boolean isFrozen) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTickingStatePacket> STREAM_CODEC = Packet.codec(
        ClientboundTickingStatePacket::write, ClientboundTickingStatePacket::new
    );

    private ClientboundTickingStatePacket(FriendlyByteBuf buf) {
        this(buf.readFloat(), buf.readBoolean());
    }

    public static ClientboundTickingStatePacket from(TickRateManager tickManager) {
        return new ClientboundTickingStatePacket(tickManager.tickrate(), tickManager.isFrozen());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.tickRate);
        buf.writeBoolean(this.isFrozen);
    }

    @Override
    public PacketType<ClientboundTickingStatePacket> type() {
        return GamePacketTypes.CLIENTBOUND_TICKING_STATE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleTickingState(this);
    }
}
