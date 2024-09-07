package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public class ClientboundSelectAdvancementsTabPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSelectAdvancementsTabPacket> STREAM_CODEC = Packet.codec(
        ClientboundSelectAdvancementsTabPacket::write, ClientboundSelectAdvancementsTabPacket::new
    );
    @Nullable
    private final ResourceLocation tab;

    public ClientboundSelectAdvancementsTabPacket(@Nullable ResourceLocation tabId) {
        this.tab = tabId;
    }

    private ClientboundSelectAdvancementsTabPacket(FriendlyByteBuf buf) {
        this.tab = buf.readNullable(FriendlyByteBuf::readResourceLocation);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeNullable(this.tab, FriendlyByteBuf::writeResourceLocation);
    }

    @Override
    public PacketType<ClientboundSelectAdvancementsTabPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SELECT_ADVANCEMENTS_TAB;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSelectAdvancementsTab(this);
    }

    @Nullable
    public ResourceLocation getTab() {
        return this.tab;
    }
}
