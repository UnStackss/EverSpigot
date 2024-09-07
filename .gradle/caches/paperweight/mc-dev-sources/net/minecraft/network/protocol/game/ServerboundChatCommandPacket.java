package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundChatCommandPacket(String command) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundChatCommandPacket> STREAM_CODEC = Packet.codec(
        ServerboundChatCommandPacket::write, ServerboundChatCommandPacket::new
    );

    private ServerboundChatCommandPacket(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.command);
    }

    @Override
    public PacketType<ServerboundChatCommandPacket> type() {
        return GamePacketTypes.SERVERBOUND_CHAT_COMMAND;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleChatCommand(this);
    }
}
