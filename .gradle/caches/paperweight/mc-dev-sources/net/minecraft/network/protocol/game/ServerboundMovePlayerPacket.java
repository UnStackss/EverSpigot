package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public abstract class ServerboundMovePlayerPacket implements Packet<ServerGamePacketListener> {
    public final double x;
    public final double y;
    public final double z;
    public final float yRot;
    public final float xRot;
    protected final boolean onGround;
    public final boolean hasPos;
    public final boolean hasRot;

    protected ServerboundMovePlayerPacket(double x, double y, double z, float yaw, float pitch, boolean onGround, boolean changePosition, boolean changeLook) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yaw;
        this.xRot = pitch;
        this.onGround = onGround;
        this.hasPos = changePosition;
        this.hasRot = changeLook;
    }

    @Override
    public abstract PacketType<? extends ServerboundMovePlayerPacket> type();

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleMovePlayer(this);
    }

    public double getX(double currentX) {
        return this.hasPos ? this.x : currentX;
    }

    public double getY(double currentY) {
        return this.hasPos ? this.y : currentY;
    }

    public double getZ(double currentZ) {
        return this.hasPos ? this.z : currentZ;
    }

    public float getYRot(float currentYaw) {
        return this.hasRot ? this.yRot : currentYaw;
    }

    public float getXRot(float currentPitch) {
        return this.hasRot ? this.xRot : currentPitch;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public boolean hasPosition() {
        return this.hasPos;
    }

    public boolean hasRotation() {
        return this.hasRot;
    }

    public static class Pos extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Pos> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.Pos::write, ServerboundMovePlayerPacket.Pos::read
        );

        public Pos(double x, double y, double z, boolean onGround) {
            super(x, y, z, 0.0F, 0.0F, onGround, true, false);
        }

        private static ServerboundMovePlayerPacket.Pos read(FriendlyByteBuf buf) {
            double d = buf.readDouble();
            double e = buf.readDouble();
            double f = buf.readDouble();
            boolean bl = buf.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Pos(d, e, f, bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            buf.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Pos> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS;
        }
    }

    public static class PosRot extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.PosRot> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.PosRot::write, ServerboundMovePlayerPacket.PosRot::read
        );

        public PosRot(double x, double y, double z, float yaw, float pitch, boolean onGround) {
            super(x, y, z, yaw, pitch, onGround, true, true);
        }

        private static ServerboundMovePlayerPacket.PosRot read(FriendlyByteBuf buf) {
            double d = buf.readDouble();
            double e = buf.readDouble();
            double f = buf.readDouble();
            float g = buf.readFloat();
            float h = buf.readFloat();
            boolean bl = buf.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.PosRot(d, e, f, g, h, bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeDouble(this.x);
            buf.writeDouble(this.y);
            buf.writeDouble(this.z);
            buf.writeFloat(this.yRot);
            buf.writeFloat(this.xRot);
            buf.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.PosRot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT;
        }
    }

    public static class Rot extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.Rot> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.Rot::write, ServerboundMovePlayerPacket.Rot::read
        );

        public Rot(float yaw, float pitch, boolean onGround) {
            super(0.0, 0.0, 0.0, yaw, pitch, onGround, false, true);
        }

        private static ServerboundMovePlayerPacket.Rot read(FriendlyByteBuf buf) {
            float f = buf.readFloat();
            float g = buf.readFloat();
            boolean bl = buf.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.Rot(f, g, bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeFloat(this.yRot);
            buf.writeFloat(this.xRot);
            buf.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.Rot> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT;
        }
    }

    public static class StatusOnly extends ServerboundMovePlayerPacket {
        public static final StreamCodec<FriendlyByteBuf, ServerboundMovePlayerPacket.StatusOnly> STREAM_CODEC = Packet.codec(
            ServerboundMovePlayerPacket.StatusOnly::write, ServerboundMovePlayerPacket.StatusOnly::read
        );

        public StatusOnly(boolean onGround) {
            super(0.0, 0.0, 0.0, 0.0F, 0.0F, onGround, false, false);
        }

        private static ServerboundMovePlayerPacket.StatusOnly read(FriendlyByteBuf buf) {
            boolean bl = buf.readUnsignedByte() != 0;
            return new ServerboundMovePlayerPacket.StatusOnly(bl);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeByte(this.onGround ? 1 : 0);
        }

        @Override
        public PacketType<ServerboundMovePlayerPacket.StatusOnly> type() {
            return GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY;
        }
    }
}
