package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class ClientboundAddEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAddEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundAddEntityPacket::write, ClientboundAddEntityPacket::new
    );
    private static final double MAGICAL_QUANTIZATION = 8000.0;
    private static final double LIMIT = 3.9;
    private final int id;
    private final UUID uuid;
    private final EntityType<?> type;
    private final double x;
    private final double y;
    private final double z;
    private final int xa;
    private final int ya;
    private final int za;
    private final byte xRot;
    private final byte yRot;
    private final byte yHeadRot;
    private final int data;

    public ClientboundAddEntityPacket(Entity entity, ServerEntity entityTrackerEntry) {
        this(entity, entityTrackerEntry, 0);
    }

    public ClientboundAddEntityPacket(Entity entity, ServerEntity entityTrackerEntry, int entityData) {
        this(
            entity.getId(),
            entity.getUUID(),
            // Paper start - fix entity tracker desync
            entity.trackingPosition().x(),
            entity.trackingPosition().y(),
            entity.trackingPosition().z(),
            // Paper end - fix entity tracker desync
            entityTrackerEntry.getLastSentXRot(),
            entityTrackerEntry.getLastSentYRot(),
            entity.getType(),
            entityData,
            entityTrackerEntry.getLastSentMovement(),
            (double)entityTrackerEntry.getLastSentYHeadRot()
        );
    }

    public ClientboundAddEntityPacket(Entity entity, int entityData, BlockPos pos) {
        this(
            entity.getId(),
            entity.getUUID(),
            (double)pos.getX(),
            (double)pos.getY(),
            (double)pos.getZ(),
            entity.getXRot(),
            entity.getYRot(),
            entity.getType(),
            entityData,
            entity.getDeltaMovement(),
            (double)entity.getYHeadRot()
        );
    }

    public ClientboundAddEntityPacket(
        int entityId, UUID uuid, double x, double y, double z, float pitch, float yaw, EntityType<?> entityType, int entityData, Vec3 velocity, double headYaw
    ) {
        this.id = entityId;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xRot = (byte)Mth.floor(pitch * 256.0F / 360.0F);
        this.yRot = (byte)Mth.floor(yaw * 256.0F / 360.0F);
        this.yHeadRot = (byte)Mth.floor(headYaw * 256.0 / 360.0);
        this.type = entityType;
        this.data = entityData;
        this.xa = (int)(Mth.clamp(velocity.x, -3.9, 3.9) * 8000.0);
        this.ya = (int)(Mth.clamp(velocity.y, -3.9, 3.9) * 8000.0);
        this.za = (int)(Mth.clamp(velocity.z, -3.9, 3.9) * 8000.0);
    }

    private ClientboundAddEntityPacket(RegistryFriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.uuid = buf.readUUID();
        this.type = ByteBufCodecs.registry(Registries.ENTITY_TYPE).decode(buf);
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.xRot = buf.readByte();
        this.yRot = buf.readByte();
        this.yHeadRot = buf.readByte();
        this.data = buf.readVarInt();
        this.xa = buf.readShort();
        this.ya = buf.readShort();
        this.za = buf.readShort();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeUUID(this.uuid);
        ByteBufCodecs.registry(Registries.ENTITY_TYPE).encode(buf, this.type);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeByte(this.xRot);
        buf.writeByte(this.yRot);
        buf.writeByte(this.yHeadRot);
        buf.writeVarInt(this.data);
        buf.writeShort(this.xa);
        buf.writeShort(this.ya);
        buf.writeShort(this.za);
    }

    @Override
    public PacketType<ClientboundAddEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_ADD_ENTITY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleAddEntity(this);
    }

    public int getId() {
        return this.id;
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public EntityType<?> getType() {
        return this.type;
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

    public double getXa() {
        return (double)this.xa / 8000.0;
    }

    public double getYa() {
        return (double)this.ya / 8000.0;
    }

    public double getZa() {
        return (double)this.za / 8000.0;
    }

    public float getXRot() {
        return (float)(this.xRot * 360) / 256.0F;
    }

    public float getYRot() {
        return (float)(this.yRot * 360) / 256.0F;
    }

    public float getYHeadRot() {
        return (float)(this.yHeadRot * 360) / 256.0F;
    }

    public int getData() {
        return this.data;
    }
}
