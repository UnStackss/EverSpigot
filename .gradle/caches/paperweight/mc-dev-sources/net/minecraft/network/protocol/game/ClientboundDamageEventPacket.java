package net.minecraft.network.protocol.game;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record ClientboundDamageEventPacket(int entityId, Holder<DamageType> sourceType, int sourceCauseId, int sourceDirectId, Optional<Vec3> sourcePosition)
    implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDamageEventPacket> STREAM_CODEC = Packet.codec(
        ClientboundDamageEventPacket::write, ClientboundDamageEventPacket::new
    );

    public ClientboundDamageEventPacket(Entity entity, DamageSource damageSource) {
        this(
            entity.getId(),
            damageSource.typeHolder(),
            damageSource.getEntity() != null ? damageSource.getEntity().getId() : -1,
            damageSource.getDirectEntity() != null ? damageSource.getDirectEntity().getId() : -1,
            Optional.ofNullable(damageSource.sourcePositionRaw())
        );
    }

    private ClientboundDamageEventPacket(RegistryFriendlyByteBuf buf) {
        this(
            buf.readVarInt(),
            DamageType.STREAM_CODEC.decode(buf),
            readOptionalEntityId(buf),
            readOptionalEntityId(buf),
            buf.readOptional(pos -> new Vec3(pos.readDouble(), pos.readDouble(), pos.readDouble()))
        );
    }

    private static void writeOptionalEntityId(FriendlyByteBuf buf, int value) {
        buf.writeVarInt(value + 1);
    }

    private static int readOptionalEntityId(FriendlyByteBuf buf) {
        return buf.readVarInt() - 1;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        DamageType.STREAM_CODEC.encode(buf, this.sourceType);
        writeOptionalEntityId(buf, this.sourceCauseId);
        writeOptionalEntityId(buf, this.sourceDirectId);
        buf.writeOptional(this.sourcePosition, (bufx, pos) -> {
            bufx.writeDouble(pos.x());
            bufx.writeDouble(pos.y());
            bufx.writeDouble(pos.z());
        });
    }

    @Override
    public PacketType<ClientboundDamageEventPacket> type() {
        return GamePacketTypes.CLIENTBOUND_DAMAGE_EVENT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleDamageEvent(this);
    }

    public DamageSource getSource(Level world) {
        if (this.sourcePosition.isPresent()) {
            return new DamageSource(this.sourceType, this.sourcePosition.get());
        } else {
            Entity entity = world.getEntity(this.sourceCauseId);
            Entity entity2 = world.getEntity(this.sourceDirectId);
            return new DamageSource(this.sourceType, entity2, entity);
        }
    }
}
