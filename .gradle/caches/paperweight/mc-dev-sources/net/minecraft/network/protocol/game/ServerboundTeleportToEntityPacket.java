package net.minecraft.network.protocol.game;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class ServerboundTeleportToEntityPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundTeleportToEntityPacket> STREAM_CODEC = Packet.codec(
        ServerboundTeleportToEntityPacket::write, ServerboundTeleportToEntityPacket::new
    );
    private final UUID uuid;

    public ServerboundTeleportToEntityPacket(UUID targetUuid) {
        this.uuid = targetUuid;
    }

    private ServerboundTeleportToEntityPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.uuid);
    }

    @Override
    public PacketType<ServerboundTeleportToEntityPacket> type() {
        return GamePacketTypes.SERVERBOUND_TELEPORT_TO_ENTITY;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleTeleportToEntityPacket(this);
    }

    @Nullable
    public Entity getEntity(ServerLevel world) {
        return world.getEntity(this.uuid);
    }
}
