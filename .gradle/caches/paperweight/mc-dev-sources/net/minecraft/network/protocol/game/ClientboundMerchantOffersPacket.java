package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.item.trading.MerchantOffers;

public class ClientboundMerchantOffersPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMerchantOffersPacket> STREAM_CODEC = Packet.codec(
        ClientboundMerchantOffersPacket::write, ClientboundMerchantOffersPacket::new
    );
    private final int containerId;
    private final MerchantOffers offers;
    private final int villagerLevel;
    private final int villagerXp;
    private final boolean showProgress;
    private final boolean canRestock;

    public ClientboundMerchantOffersPacket(int syncId, MerchantOffers offers, int levelProgress, int experience, boolean leveled, boolean refreshable) {
        this.containerId = syncId;
        this.offers = offers.copy();
        this.villagerLevel = levelProgress;
        this.villagerXp = experience;
        this.showProgress = leveled;
        this.canRestock = refreshable;
    }

    private ClientboundMerchantOffersPacket(RegistryFriendlyByteBuf buf) {
        this.containerId = buf.readVarInt();
        this.offers = MerchantOffers.STREAM_CODEC.decode(buf);
        this.villagerLevel = buf.readVarInt();
        this.villagerXp = buf.readVarInt();
        this.showProgress = buf.readBoolean();
        this.canRestock = buf.readBoolean();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.containerId);
        MerchantOffers.STREAM_CODEC.encode(buf, this.offers);
        buf.writeVarInt(this.villagerLevel);
        buf.writeVarInt(this.villagerXp);
        buf.writeBoolean(this.showProgress);
        buf.writeBoolean(this.canRestock);
    }

    @Override
    public PacketType<ClientboundMerchantOffersPacket> type() {
        return GamePacketTypes.CLIENTBOUND_MERCHANT_OFFERS;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleMerchantOffers(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public MerchantOffers getOffers() {
        return this.offers;
    }

    public int getVillagerLevel() {
        return this.villagerLevel;
    }

    public int getVillagerXp() {
        return this.villagerXp;
    }

    public boolean showProgress() {
        return this.showProgress;
    }

    public boolean canRestock() {
        return this.canRestock;
    }
}
