package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClientboundSetEntityMotionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetEntityMotionPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityMotionPacket::write, ClientboundSetEntityMotionPacket::new
    );
    private final int id;
    private final int xa;
    private final int ya;
    private final int za;

    public ClientboundSetEntityMotionPacket(Entity entity) {
        this(entity.getId(), entity.getDeltaMovement());
    }

    public ClientboundSetEntityMotionPacket(int entityId, Vec3 velocity) {
        this.id = entityId;
        double d = 3.9;
        double e = Mth.clamp(velocity.x, -3.9, 3.9);
        double f = Mth.clamp(velocity.y, -3.9, 3.9);
        double g = Mth.clamp(velocity.z, -3.9, 3.9);
        this.xa = (int)(e * 8000.0);
        this.ya = (int)(f * 8000.0);
        this.za = (int)(g * 8000.0);
    }

    private ClientboundSetEntityMotionPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.xa = buf.readShort();
        this.ya = buf.readShort();
        this.za = buf.readShort();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeShort(this.xa);
        buf.writeShort(this.ya);
        buf.writeShort(this.za);
    }

    @Override
    public PacketType<ClientboundSetEntityMotionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_MOTION;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetEntityMotion(this);
    }

    public int getId() {
        return this.id;
    }

    public double getXa() {
        return (double)this.xa / 8000.0;
    }

    public double getYa() {
        return (double)this.ya / 8000.0;
    }

    public double getZa() {
        return (double)this.za / 8000.0;
    }
}
