// mc-dev import
package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.InteractionHand;

public class ServerboundUseItemPacket implements Packet<ServerGamePacketListener> {

    public static final StreamCodec<FriendlyByteBuf, ServerboundUseItemPacket> STREAM_CODEC = Packet.codec(ServerboundUseItemPacket::write, ServerboundUseItemPacket::new);
    private final InteractionHand hand;
    private final int sequence;
    private final float yRot;
    private final float xRot;
    public long timestamp; // Spigot

    public ServerboundUseItemPacket(InteractionHand hand, int sequence, float yaw, float pitch) {
        this.hand = hand;
        this.sequence = sequence;
        this.yRot = yaw;
        this.xRot = pitch;
    }

    private ServerboundUseItemPacket(FriendlyByteBuf buf) {
        this.timestamp = System.currentTimeMillis(); // Spigot
        this.hand = (InteractionHand) buf.readEnum(InteractionHand.class);
        this.sequence = buf.readVarInt();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.hand);
        buf.writeVarInt(this.sequence);
        buf.writeFloat(this.yRot);
        buf.writeFloat(this.xRot);
    }

    @Override
    public PacketType<ServerboundUseItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_USE_ITEM;
    }

    public void handle(ServerGamePacketListener listener) {
        listener.handleUseItem(this);
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public int getSequence() {
        return this.sequence;
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getXRot() {
        return this.xRot;
    }
}
