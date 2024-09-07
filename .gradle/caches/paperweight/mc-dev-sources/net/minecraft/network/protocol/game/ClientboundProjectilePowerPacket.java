package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundProjectilePowerPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundProjectilePowerPacket> STREAM_CODEC = Packet.codec(
        ClientboundProjectilePowerPacket::write, ClientboundProjectilePowerPacket::new
    );
    private final int id;
    private final double accelerationPower;

    public ClientboundProjectilePowerPacket(int entityId, double accelerationPower) {
        this.id = entityId;
        this.accelerationPower = accelerationPower;
    }

    private ClientboundProjectilePowerPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.accelerationPower = buf.readDouble();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeDouble(this.accelerationPower);
    }

    @Override
    public PacketType<ClientboundProjectilePowerPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PROJECTILE_POWER;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleProjectilePowerPacket(this);
    }

    public int getId() {
        return this.id;
    }

    public double getAccelerationPower() {
        return this.accelerationPower;
    }
}
