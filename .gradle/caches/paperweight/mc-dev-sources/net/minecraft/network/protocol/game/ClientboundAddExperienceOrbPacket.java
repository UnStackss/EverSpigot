package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

public class ClientboundAddExperienceOrbPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundAddExperienceOrbPacket> STREAM_CODEC = Packet.codec(
        ClientboundAddExperienceOrbPacket::write, ClientboundAddExperienceOrbPacket::new
    );
    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final int value;

    public ClientboundAddExperienceOrbPacket(ExperienceOrb orb, ServerEntity entry) {
        this.id = orb.getId();
        Vec3 vec3 = entry.getPositionBase();
        this.x = vec3.x();
        this.y = vec3.y();
        this.z = vec3.z();
        this.value = orb.getValue();
    }

    private ClientboundAddExperienceOrbPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.value = buf.readShort();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeShort(this.value);
    }

    @Override
    public PacketType<ClientboundAddExperienceOrbPacket> type() {
        return GamePacketTypes.CLIENTBOUND_ADD_EXPERIENCE_ORB;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleAddExperienceOrb(this);
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

    public int getValue() {
        return this.value;
    }
}
