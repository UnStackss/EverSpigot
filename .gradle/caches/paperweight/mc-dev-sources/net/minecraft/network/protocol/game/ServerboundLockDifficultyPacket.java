package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundLockDifficultyPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundLockDifficultyPacket> STREAM_CODEC = Packet.codec(
        ServerboundLockDifficultyPacket::write, ServerboundLockDifficultyPacket::new
    );
    private final boolean locked;

    public ServerboundLockDifficultyPacket(boolean difficultyLocked) {
        this.locked = difficultyLocked;
    }

    private ServerboundLockDifficultyPacket(FriendlyByteBuf buf) {
        this.locked = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.locked);
    }

    @Override
    public PacketType<ServerboundLockDifficultyPacket> type() {
        return GamePacketTypes.SERVERBOUND_LOCK_DIFFICULTY;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleLockDifficulty(this);
    }

    public boolean isLocked() {
        return this.locked;
    }
}
