package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClientboundTeleportEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTeleportEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundTeleportEntityPacket::write, ClientboundTeleportEntityPacket::new
    );
    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final byte yRot;
    private final byte xRot;
    private final boolean onGround;

    public ClientboundTeleportEntityPacket(Entity entity) {
        this.id = entity.getId();
        Vec3 vec3 = entity.trackingPosition();
        this.x = vec3.x;
        this.y = vec3.y;
        this.z = vec3.z;
        this.yRot = (byte)((int)(entity.getYRot() * 256.0F / 360.0F));
        this.xRot = (byte)((int)(entity.getXRot() * 256.0F / 360.0F));
        this.onGround = entity.onGround();
    }

    private ClientboundTeleportEntityPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yRot = buf.readByte();
        this.xRot = buf.readByte();
        this.onGround = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeByte(this.yRot);
        buf.writeByte(this.xRot);
        buf.writeBoolean(this.onGround);
    }

    @Override
    public PacketType<ClientboundTeleportEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_TELEPORT_ENTITY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleTeleportEntity(this);
    }

    public int getId() {
        return this.id;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public byte getyRot() {
        return this.yRot;
    }

    public byte getxRot() {
        return this.xRot;
    }

    public boolean isOnGround() {
        return this.onGround;
    }
}
