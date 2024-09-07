package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public abstract class ClientboundMoveEntityPacket implements Packet<ClientGamePacketListener> {
    protected final int entityId;
    protected final short xa;
    protected final short ya;
    protected final short za;
    protected final byte yRot;
    protected final byte xRot;
    protected final boolean onGround;
    protected final boolean hasRot;
    protected final boolean hasPos;

    protected ClientboundMoveEntityPacket(
        int entityId, short deltaX, short deltaY, short deltaZ, byte yaw, byte pitch, boolean onGround, boolean rotate, boolean positionChanged
    ) {
        this.entityId = entityId;
        this.xa = deltaX;
        this.ya = deltaY;
        this.za = deltaZ;
        this.yRot = yaw;
        this.xRot = pitch;
        this.onGround = onGround;
        this.hasRot = rotate;
        this.hasPos = positionChanged;
    }

    @Override
    public abstract PacketType<? extends ClientboundMoveEntityPacket> type();

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleMoveEntity(this);
    }

    @Override
    public String toString() {
        return "Entity_" + super.toString();
    }

    @Nullable
    public Entity getEntity(Level world) {
        return world.getEntity(this.entityId);
    }

    public short getXa() {
        return this.xa;
    }

    public short getYa() {
        return this.ya;
    }

    public short getZa() {
        return this.za;
    }

    public byte getyRot() {
        return this.yRot;
    }

    public byte getxRot() {
        return this.xRot;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public static class Pos extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Pos> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.Pos::write, ClientboundMoveEntityPacket.Pos::read
        );

        public Pos(int entityId, short deltaX, short deltaY, short deltaZ, boolean onGround) {
            super(entityId, deltaX, deltaY, deltaZ, (byte)0, (byte)0, onGround, false, true);
        }

        private static ClientboundMoveEntityPacket.Pos read(FriendlyByteBuf buf) {
            int i = buf.readVarInt();
            short s = buf.readShort();
            short t = buf.readShort();
            short u = buf.readShort();
            boolean bl = buf.readBoolean();
            return new ClientboundMoveEntityPacket.Pos(i, s, t, u, bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.entityId);
            buf.writeShort(this.xa);
            buf.writeShort(this.ya);
            buf.writeShort(this.za);
            buf.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Pos> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS;
        }
    }

    public static class PosRot extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.PosRot> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.PosRot::write, ClientboundMoveEntityPacket.PosRot::read
        );

        public PosRot(int entityId, short deltaX, short deltaY, short deltaZ, byte yaw, byte pitch, boolean onGround) {
            super(entityId, deltaX, deltaY, deltaZ, yaw, pitch, onGround, true, true);
        }

        private static ClientboundMoveEntityPacket.PosRot read(FriendlyByteBuf buf) {
            int i = buf.readVarInt();
            short s = buf.readShort();
            short t = buf.readShort();
            short u = buf.readShort();
            byte b = buf.readByte();
            byte c = buf.readByte();
            boolean bl = buf.readBoolean();
            return new ClientboundMoveEntityPacket.PosRot(i, s, t, u, b, c, bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.entityId);
            buf.writeShort(this.xa);
            buf.writeShort(this.ya);
            buf.writeShort(this.za);
            buf.writeByte(this.yRot);
            buf.writeByte(this.xRot);
            buf.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.PosRot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_POS_ROT;
        }
    }

    public static class Rot extends ClientboundMoveEntityPacket {
        public static final StreamCodec<FriendlyByteBuf, ClientboundMoveEntityPacket.Rot> STREAM_CODEC = Packet.codec(
            ClientboundMoveEntityPacket.Rot::write, ClientboundMoveEntityPacket.Rot::read
        );

        public Rot(int entityId, byte yaw, byte pitch, boolean onGround) {
            super(entityId, (short)0, (short)0, (short)0, yaw, pitch, onGround, true, false);
        }

        private static ClientboundMoveEntityPacket.Rot read(FriendlyByteBuf buf) {
            int i = buf.readVarInt();
            byte b = buf.readByte();
            byte c = buf.readByte();
            boolean bl = buf.readBoolean();
            return new ClientboundMoveEntityPacket.Rot(i, b, c, bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.entityId);
            buf.writeByte(this.yRot);
            buf.writeByte(this.xRot);
            buf.writeBoolean(this.onGround);
        }

        @Override
        public PacketType<ClientboundMoveEntityPacket.Rot> type() {
            return GamePacketTypes.CLIENTBOUND_MOVE_ENTITY_ROT;
        }
    }
}
