package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ServerboundPlaceRecipePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPlaceRecipePacket> STREAM_CODEC = Packet.codec(
        ServerboundPlaceRecipePacket::write, ServerboundPlaceRecipePacket::new
    );
    private final int containerId;
    private final ResourceLocation recipe;
    private final boolean shiftDown;

    public ServerboundPlaceRecipePacket(int syncId, RecipeHolder<?> recipe, boolean craftAll) {
        this.containerId = syncId;
        this.recipe = recipe.id();
        this.shiftDown = craftAll;
    }

    private ServerboundPlaceRecipePacket(FriendlyByteBuf buf) {
        this.containerId = buf.readByte();
        this.recipe = buf.readResourceLocation();
        this.shiftDown = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeResourceLocation(this.recipe);
        buf.writeBoolean(this.shiftDown);
    }

    @Override
    public PacketType<ServerboundPlaceRecipePacket> type() {
        return GamePacketTypes.SERVERBOUND_PLACE_RECIPE;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handlePlaceRecipe(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public ResourceLocation getRecipe() {
        return this.recipe;
    }

    public boolean isShiftDown() {
        return this.shiftDown;
    }
}
