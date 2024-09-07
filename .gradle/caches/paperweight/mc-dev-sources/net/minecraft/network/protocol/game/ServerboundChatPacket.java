package net.minecraft.network.protocol.game;

import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatPacket(String message, Instant timeStamp, long salt, @Nullable MessageSignature signature, LastSeenMessages.Update lastSeenMessages)
    implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatPacket::write, ServerboundChatPacket::new
    );

    private ServerboundChatPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(256), buf.readInstant(), buf.readLong(), buf.readNullable(MessageSignature::read), new LastSeenMessages.Update(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.message, 256);
        buf.writeInstant(this.timeStamp);
        buf.writeLong(this.salt);
        buf.writeNullable(this.signature, MessageSignature::write);
        this.lastSeenMessages.write(buf);
    }

    @Override
    public PacketType<ServerboundChatPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleChat(this);
    }
}
