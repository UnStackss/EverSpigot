package net.minecraft.network.protocol.game;

import java.time.Instant;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatCommandSignedPacket(
    String command, Instant timeStamp, long salt, ArgumentSignatures argumentSignatures, LastSeenMessages.Update lastSeenMessages
) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatCommandSignedPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatCommandSignedPacket::write, ServerboundChatCommandSignedPacket::new
    );

    private ServerboundChatCommandSignedPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readInstant(), buf.readLong(), new ArgumentSignatures(buf), new LastSeenMessages.Update(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.command);
        buf.writeInstant(this.timeStamp);
        buf.writeLong(this.salt);
        this.argumentSignatures.write(buf);
        this.lastSeenMessages.write(buf);
    }

    @Override
    public PacketType<ServerboundChatCommandSignedPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT_COMMAND_SIGNED;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleSignedChatCommand(this);
    }
}
