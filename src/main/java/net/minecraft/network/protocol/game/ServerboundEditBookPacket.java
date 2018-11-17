package net.minecraft.network.protocol.game;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public record ServerboundEditBookPacket(int slot, List<String> pages, Optional<String> title) implements Packet<ServerGamePacketListener> {
    public static final int MAX_BYTES_PER_CHAR = 4;
    private static final int TITLE_MAX_CHARS = 128;
    private static final int PAGE_MAX_CHARS = 8192;
    private static final int MAX_PAGES_COUNT = 200;
    public static final StreamCodec<FriendlyByteBuf, ServerboundEditBookPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ServerboundEditBookPacket::slot,
        ByteBufCodecs.stringUtf8(net.minecraft.world.item.component.WritableBookContent.PAGE_EDIT_LENGTH).apply(ByteBufCodecs.list(net.minecraft.world.item.component.WritableBookContent.MAX_PAGES)), // Paper - limit books
        ServerboundEditBookPacket::pages,
        ByteBufCodecs.stringUtf8(net.minecraft.world.item.component.WrittenBookContent.TITLE_MAX_LENGTH).apply(ByteBufCodecs::optional), // Paper - limit books
        ServerboundEditBookPacket::title,
        ServerboundEditBookPacket::new
    );

    public ServerboundEditBookPacket(int slot, List<String> pages, Optional<String> title) {
        pages = List.copyOf(pages);
        this.slot = slot;
        this.pages = pages;
        this.title = title;
    }

    @Override
    public PacketType<ServerboundEditBookPacket> type() {
        return GamePacketTypes.SERVERBOUND_EDIT_BOOK;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleEditBook(this);
    }
}
