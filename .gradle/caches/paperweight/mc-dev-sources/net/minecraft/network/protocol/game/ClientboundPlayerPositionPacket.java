package net.minecraft.network.protocol.game;

import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.RelativeMovement;

public class ClientboundPlayerPositionPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerPositionPacket::write, ClientboundPlayerPositionPacket::new
    );
    private final double x;
    private final double y;
    private final double z;
    private final float yRot;
    private final float xRot;
    private final Set<RelativeMovement> relativeArguments;
    private final int id;

    public ClientboundPlayerPositionPacket(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> flags, int teleportId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yaw;
        this.xRot = pitch;
        this.relativeArguments = flags;
        this.id = teleportId;
    }

    private ClientboundPlayerPositionPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
        this.relativeArguments = RelativeMovement.unpack(buf.readUnsignedByte());
        this.id = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yRot);
        buf.writeFloat(this.xRot);
        buf.writeByte(RelativeMovement.pack(this.relativeArguments));
        buf.writeVarInt(this.id);
    }

    @Override
    public PacketType<ClientboundPlayerPositionPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_POSITION;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleMovePlayer(this);
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

    public float getYRot() {
        return this.yRot;
    }

    public float getXRot() {
        return this.xRot;
    }

    public int getId() {
        return this.id;
    }

    public Set<RelativeMovement> getRelativeArguments() {
        return this.relativeArguments;
    }
}
