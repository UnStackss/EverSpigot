package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;

public class ClientboundSetEntityLinkPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetEntityLinkPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityLinkPacket::write, ClientboundSetEntityLinkPacket::new
    );
    private final int sourceId;
    private final int destId;

    public ClientboundSetEntityLinkPacket(Entity attachedEntity, @Nullable Entity holdingEntity) {
        this.sourceId = attachedEntity.getId();
        this.destId = holdingEntity != null ? holdingEntity.getId() : 0;
    }

    private ClientboundSetEntityLinkPacket(FriendlyByteBuf buf) {
        this.sourceId = buf.readInt();
        this.destId = buf.readInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.sourceId);
        buf.writeInt(this.destId);
    }

    @Override
    public PacketType<ClientboundSetEntityLinkPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_LINK;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleEntityLinkPacket(this);
    }

    public int getSourceId() {
        return this.sourceId;
    }

    public int getDestId() {
        return this.destId;
    }
}
