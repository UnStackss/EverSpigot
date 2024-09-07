package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPaddleBoatPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPaddleBoatPacket> STREAM_CODEC = Packet.codec(
        ServerboundPaddleBoatPacket::write, ServerboundPaddleBoatPacket::new
    );
    private final boolean left;
    private final boolean right;

    public ServerboundPaddleBoatPacket(boolean leftPaddling, boolean rightPaddling) {
        this.left = leftPaddling;
        this.right = rightPaddling;
    }

    private ServerboundPaddleBoatPacket(FriendlyByteBuf buf) {
        this.left = buf.readBoolean();
        this.right = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.left);
        buf.writeBoolean(this.right);
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handlePaddleBoat(this);
    }

    @Override
    public PacketType<ServerboundPaddleBoatPacket> type() {
        return GamePacketTypes.SERVERBOUND_PADDLE_BOAT;
    }

    public boolean getLeft() {
        return this.left;
    }

    public boolean getRight() {
        return this.right;
    }
}
