package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.inventory.RecipeBookType;

public class ServerboundRecipeBookChangeSettingsPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundRecipeBookChangeSettingsPacket> STREAM_CODEC = Packet.codec(
        ServerboundRecipeBookChangeSettingsPacket::write, ServerboundRecipeBookChangeSettingsPacket::new
    );
    private final RecipeBookType bookType;
    private final boolean isOpen;
    private final boolean isFiltering;

    public ServerboundRecipeBookChangeSettingsPacket(RecipeBookType category, boolean guiOpen, boolean filteringCraftable) {
        this.bookType = category;
        this.isOpen = guiOpen;
        this.isFiltering = filteringCraftable;
    }

    private ServerboundRecipeBookChangeSettingsPacket(FriendlyByteBuf buf) {
        this.bookType = buf.readEnum(RecipeBookType.class);
        this.isOpen = buf.readBoolean();
        this.isFiltering = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.bookType);
        buf.writeBoolean(this.isOpen);
        buf.writeBoolean(this.isFiltering);
    }

    @Override
    public PacketType<ServerboundRecipeBookChangeSettingsPacket> type() {
        return GamePacketTypes.SERVERBOUND_RECIPE_BOOK_CHANGE_SETTINGS;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleRecipeBookChangeSettingsPacket(this);
    }

    public RecipeBookType getBookType() {
        return this.bookType;
    }

    public boolean isOpen() {
        return this.isOpen;
    }

    public boolean isFiltering() {
        return this.isFiltering;
    }
}
