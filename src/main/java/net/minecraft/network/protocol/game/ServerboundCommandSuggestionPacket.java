package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundCommandSuggestionPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundCommandSuggestionPacket> STREAM_CODEC = Packet.codec(
        ServerboundCommandSuggestionPacket::write, ServerboundCommandSuggestionPacket::new
    );
    private final int id;
    private final String command;

    public ServerboundCommandSuggestionPacket(int completionId, String partialCommand) {
        this.id = completionId;
        this.command = partialCommand;
    }

    private ServerboundCommandSuggestionPacket(FriendlyByteBuf buf) {
        this.id = buf.readVarInt();
        this.command = buf.readUtf(2048); // Paper
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeUtf(this.command, 32500);
    }

    @Override
    public PacketType<ServerboundCommandSuggestionPacket> type() {
        return GamePacketTypes.SERVERBOUND_COMMAND_SUGGESTION;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleCustomCommandSuggestions(this);
    }

    public int getId() {
        return this.id;
    }

    public String getCommand() {
        return this.command;
    }
}
