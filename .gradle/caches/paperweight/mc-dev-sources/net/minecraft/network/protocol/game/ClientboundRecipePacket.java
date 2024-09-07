package net.minecraft.network.protocol.game;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBookSettings;

public class ClientboundRecipePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundRecipePacket> STREAM_CODEC = Packet.codec(
        ClientboundRecipePacket::write, ClientboundRecipePacket::new
    );
    private final ClientboundRecipePacket.State state;
    private final List<ResourceLocation> recipes;
    private final List<ResourceLocation> toHighlight;
    private final RecipeBookSettings bookSettings;

    public ClientboundRecipePacket(
        ClientboundRecipePacket.State action,
        Collection<ResourceLocation> recipeIdsToChange,
        Collection<ResourceLocation> recipeIdsToInit,
        RecipeBookSettings options
    ) {
        this.state = action;
        this.recipes = ImmutableList.copyOf(recipeIdsToChange);
        this.toHighlight = ImmutableList.copyOf(recipeIdsToInit);
        this.bookSettings = options;
    }

    private ClientboundRecipePacket(FriendlyByteBuf buf) {
        this.state = buf.readEnum(ClientboundRecipePacket.State.class);
        this.bookSettings = RecipeBookSettings.read(buf);
        this.recipes = buf.readList(FriendlyByteBuf::readResourceLocation);
        if (this.state == ClientboundRecipePacket.State.INIT) {
            this.toHighlight = buf.readList(FriendlyByteBuf::readResourceLocation);
        } else {
            this.toHighlight = ImmutableList.of();
        }
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.state);
        this.bookSettings.write(buf);
        buf.writeCollection(this.recipes, FriendlyByteBuf::writeResourceLocation);
        if (this.state == ClientboundRecipePacket.State.INIT) {
            buf.writeCollection(this.toHighlight, FriendlyByteBuf::writeResourceLocation);
        }
    }

    @Override
    public PacketType<ClientboundRecipePacket> type() {
        return GamePacketTypes.CLIENTBOUND_RECIPE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleAddOrRemoveRecipes(this);
    }

    public List<ResourceLocation> getRecipes() {
        return this.recipes;
    }

    public List<ResourceLocation> getHighlights() {
        return this.toHighlight;
    }

    public RecipeBookSettings getBookSettings() {
        return this.bookSettings;
    }

    public ClientboundRecipePacket.State getState() {
        return this.state;
    }

    public static enum State {
        INIT,
        ADD,
        REMOVE;
    }
}
