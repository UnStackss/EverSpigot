package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundSetSimulationDistancePacket(int simulationDistance) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetSimulationDistancePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetSimulationDistancePacket::write, ClientboundSetSimulationDistancePacket::new
    );

    private ClientboundSetSimulationDistancePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.simulationDistance);
    }

    @Override
    public PacketType<ClientboundSetSimulationDistancePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_SIMULATION_DISTANCE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetSimulationDistance(this);
    }
}
