package net.minecraft.network.protocol.game;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundCustomChatCompletionsPacket(ClientboundCustomChatCompletionsPacket.Action action, List<String> entries)
    implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundCustomChatCompletionsPacket> STREAM_CODEC = Packet.codec(
        ClientboundCustomChatCompletionsPacket::write, ClientboundCustomChatCompletionsPacket::new
    );

    private ClientboundCustomChatCompletionsPacket(FriendlyByteBuf buf) {
        this(buf.readEnum(ClientboundCustomChatCompletionsPacket.Action.class), buf.readList(FriendlyByteBuf::readUtf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeCollection(this.entries, FriendlyByteBuf::writeUtf);
    }

    @Override
    public PacketType<ClientboundCustomChatCompletionsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CUSTOM_CHAT_COMPLETIONS;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleCustomChatCompletions(this);
    }

    public static enum Action {
        ADD,
        REMOVE,
        SET;
    }
}
