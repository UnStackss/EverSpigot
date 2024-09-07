package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class ClientboundPlaceGhostRecipePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlaceGhostRecipePacket> STREAM_CODEC = Packet.codec(
        ClientboundPlaceGhostRecipePacket::write, ClientboundPlaceGhostRecipePacket::new
    );
    private final int containerId;
    private final ResourceLocation recipe;

    public ClientboundPlaceGhostRecipePacket(int syncId, RecipeHolder<?> recipe) {
        this.containerId = syncId;
        this.recipe = recipe.id();
    }

    private ClientboundPlaceGhostRecipePacket(FriendlyByteBuf buf) {
        this.containerId = buf.readByte();
        this.recipe = buf.readResourceLocation();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeResourceLocation(this.recipe);
    }

    @Override
    public PacketType<ClientboundPlaceGhostRecipePacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLACE_GHOST_RECIPE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handlePlaceRecipe(this);
    }

    public ResourceLocation getRecipe() {
        return this.recipe;
    }

    public int getContainerId() {
        return this.containerId;
    }
}
