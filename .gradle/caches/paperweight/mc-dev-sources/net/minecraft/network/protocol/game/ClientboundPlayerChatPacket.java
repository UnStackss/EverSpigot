package net.minecraft.network.protocol.game;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ClientboundPlayerChatPacket(
    UUID sender,
    int index,
    @Nullable MessageSignature signature,
    SignedMessageBody.Packed body,
    @Nullable Component unsignedContent,
    FilterMask filterMask,
    ChatType.Bound chatType
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerChatPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerChatPacket::write, ClientboundPlayerChatPacket::new
    );

    private ClientboundPlayerChatPacket(RegistryFriendlyByteBuf buf) {
        this(
            buf.readUUID(),
            buf.readVarInt(),
            buf.readNullable(MessageSignature::read),
            new SignedMessageBody.Packed(buf),
            FriendlyByteBuf.readNullable(buf, ComponentSerialization.TRUSTED_STREAM_CODEC),
            FilterMask.read(buf),
            ChatType.Bound.STREAM_CODEC.decode(buf)
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.sender);
        buf.writeVarInt(this.index);
        buf.writeNullable(this.signature, MessageSignature::write);
        this.body.write(buf);
        FriendlyByteBuf.writeNullable(buf, this.unsignedContent, ComponentSerialization.TRUSTED_STREAM_CODEC);
        FilterMask.write(buf, this.filterMask);
        ChatType.Bound.STREAM_CODEC.encode(buf, this.chatType);
    }

    @Override
    public PacketType<ClientboundPlayerChatPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_CHAT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handlePlayerChat(this);
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
