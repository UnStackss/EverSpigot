package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetHealthPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetHealthPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetHealthPacket::write, ClientboundSetHealthPacket::new
    );
    private final float health;
    private final int food;
    private final float saturation;

    public ClientboundSetHealthPacket(float health, int food, float saturation) {
        this.health = health;
        this.food = food;
        this.saturation = saturation;
    }

    private ClientboundSetHealthPacket(FriendlyByteBuf buf) {
        this.health = buf.readFloat();
        this.food = buf.readVarInt();
        this.saturation = buf.readFloat();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.health);
        buf.writeVarInt(this.food);
        buf.writeFloat(this.saturation);
    }

    @Override
    public PacketType<ClientboundSetHealthPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_HEALTH;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetHealth(this);
    }

    public float getHealth() {
        return this.health;
    }

    public int getFood() {
        return this.food;
    }

    public float getSaturation() {
        return this.saturation;
    }
}
