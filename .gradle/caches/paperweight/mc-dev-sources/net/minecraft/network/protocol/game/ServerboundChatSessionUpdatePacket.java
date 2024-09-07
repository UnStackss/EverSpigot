package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatSessionUpdatePacket(RemoteChatSession.Data chatSession) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatSessionUpdatePacket> STREAM_CODEC = Packet.codec(
        ServerboundChatSessionUpdatePacket::write, ServerboundChatSessionUpdatePacket::new
    );

    private ServerboundChatSessionUpdatePacket(FriendlyByteBuf buf) {
        this(RemoteChatSession.Data.read(buf));
    }

    private void write(FriendlyByteBuf buf) {
        RemoteChatSession.Data.write(buf, this.chatSession);
    }

    @Override
    public PacketType<ServerboundChatSessionUpdatePacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT_SESSION_UPDATE;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleChatSessionUpdate(this);
    }
}
