package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public class ClientboundEntityEventPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundEntityEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundEntityEventPacket::write, ClientboundEntityEventPacket::new
    );
    private final int entityId;
    private final byte eventId;

    public ClientboundEntityEventPacket(Entity entity, byte status) {
        this.entityId = entity.getId();
        this.eventId = status;
    }

    private ClientboundEntityEventPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.eventId = buf.readByte();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeByte(this.eventId);
    }

    @Override
    public PacketType<ClientboundEntityEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_ENTITY_EVENT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleEntityEvent(this);
    }

    @Nullable
    public Entity getEntity(Level world) {
        return world.getEntity(this.entityId);
    }

    public byte getEventId() {
        return this.eventId;
    }
}
