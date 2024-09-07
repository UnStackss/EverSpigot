package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundRenameItemPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundRenameItemPacket> STREAM_CODEC = Packet.codec(
        ServerboundRenameItemPacket::write, ServerboundRenameItemPacket::new
    );
    private final String name;

    public ServerboundRenameItemPacket(String name) {
        this.name = name;
    }

    private ServerboundRenameItemPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.name);
    }

    @Override
    public PacketType<ServerboundRenameItemPacket> type() {
        return GamePacketTypes.SERVERBOUND_RENAME_ITEM;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleRenameItem(this);
    }

    public String getName() {
        return this.name;
    }
}
