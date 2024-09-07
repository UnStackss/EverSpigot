package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPlayerInputPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPlayerInputPacket> STREAM_CODEC = Packet.codec(
        ServerboundPlayerInputPacket::write, ServerboundPlayerInputPacket::new
    );
    private static final int FLAG_JUMPING = 1;
    private static final int FLAG_SHIFT_KEY_DOWN = 2;
    private final float xxa;
    private final float zza;
    private final boolean isJumping;
    private final boolean isShiftKeyDown;

    public ServerboundPlayerInputPacket(float sideways, float forward, boolean jumping, boolean sneaking) {
        this.xxa = sideways;
        this.zza = forward;
        this.isJumping = jumping;
        this.isShiftKeyDown = sneaking;
    }

    private ServerboundPlayerInputPacket(FriendlyByteBuf buf) {
        this.xxa = buf.readFloat();
        this.zza = buf.readFloat();
        byte b = buf.readByte();
        this.isJumping = (b & 1) > 0;
        this.isShiftKeyDown = (b & 2) > 0;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.xxa);
        buf.writeFloat(this.zza);
        byte b = 0;
        if (this.isJumping) {
            b = (byte)(b | 1);
        }

        if (this.isShiftKeyDown) {
            b = (byte)(b | 2);
        }

        buf.writeByte(b);
    }

    @Override
    public PacketType<ServerboundPlayerInputPacket> type() {
        return GamePacketTypes.SERVERBOUND_PLAYER_INPUT;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handlePlayerInput(this);
    }

    public float getXxa() {
        return this.xxa;
    }

    public float getZza() {
        return this.zza;
    }

    public boolean isJumping() {
        return this.isJumping;
    }

    public boolean isShiftKeyDown() {
        return this.isShiftKeyDown;
    }
}
