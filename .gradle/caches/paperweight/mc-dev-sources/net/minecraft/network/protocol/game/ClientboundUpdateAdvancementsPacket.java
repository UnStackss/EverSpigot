package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;

public class ClientboundUpdateAdvancementsPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateAdvancementsPacket> STREAM_CODEC = Packet.codec(
        ClientboundUpdateAdvancementsPacket::write, ClientboundUpdateAdvancementsPacket::new
    );
    private final boolean reset;
    private final List<AdvancementHolder> added;
    private final Set<ResourceLocation> removed;
    private final Map<ResourceLocation, AdvancementProgress> progress;

    public ClientboundUpdateAdvancementsPacket(
        boolean clearCurrent, Collection<AdvancementHolder> toEarn, Set<ResourceLocation> toRemove, Map<ResourceLocation, AdvancementProgress> toSetProgress
    ) {
        this.reset = clearCurrent;
        this.added = List.copyOf(toEarn);
        this.removed = Set.copyOf(toRemove);
        this.progress = Map.copyOf(toSetProgress);
    }

    private ClientboundUpdateAdvancementsPacket(RegistryFriendlyByteBuf buf) {
        this.reset = buf.readBoolean();
        this.added = AdvancementHolder.LIST_STREAM_CODEC.decode(buf);
        this.removed = buf.readCollection(Sets::newLinkedHashSetWithExpectedSize, FriendlyByteBuf::readResourceLocation);
        this.progress = buf.readMap(FriendlyByteBuf::readResourceLocation, AdvancementProgress::fromNetwork);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.reset);
        AdvancementHolder.LIST_STREAM_CODEC.encode(buf, this.added);
        buf.writeCollection(this.removed, FriendlyByteBuf::writeResourceLocation);
        buf.writeMap(this.progress, FriendlyByteBuf::writeResourceLocation, (buf2, progress) -> progress.serializeToNetwork(buf2));
    }

    @Override
    public PacketType<ClientboundUpdateAdvancementsPacket> type() {
        return GamePacketTypes.CLIENTBOUND_UPDATE_ADVANCEMENTS;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleUpdateAdvancementsPacket(this);
    }

    public List<AdvancementHolder> getAdded() {
        return this.added;
    }

    public Set<ResourceLocation> getRemoved() {
        return this.removed;
    }

    public Map<ResourceLocation, AdvancementProgress> getProgress() {
        return this.progress;
    }

    public boolean shouldReset() {
        return this.reset;
    }
}
