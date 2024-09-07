package net.minecraft.server.network.config;

import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.TagNetworkSerialization;

public class SynchronizeRegistriesTask implements ConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("synchronize_registries");
    private final List<KnownPack> requestedPacks;
    private final LayeredRegistryAccess<RegistryLayer> registries;

    public SynchronizeRegistriesTask(List<KnownPack> knownPacks, LayeredRegistryAccess<RegistryLayer> registries) {
        this.requestedPacks = knownPacks;
        this.registries = registries;
    }

    @Override
    public void start(Consumer<Packet<?>> sender) {
        sender.accept(new ClientboundSelectKnownPacks(this.requestedPacks));
    }

    private void sendRegistries(Consumer<Packet<?>> sender, Set<KnownPack> commonKnownPacks) {
        DynamicOps<Tag> dynamicOps = this.registries.compositeAccess().createSerializationContext(NbtOps.INSTANCE);
        RegistrySynchronization.packRegistries(
            dynamicOps,
            this.registries.getAccessFrom(RegistryLayer.WORLDGEN),
            commonKnownPacks,
            (key, entries) -> sender.accept(new ClientboundRegistryDataPacket(key, entries))
        );
        sender.accept(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
    }

    public void handleResponse(List<KnownPack> clientKnownPacks, Consumer<Packet<?>> sender) {
        if (clientKnownPacks.equals(this.requestedPacks)) {
            this.sendRegistries(sender, Set.copyOf(this.requestedPacks));
        } else {
            this.sendRegistries(sender, Set.of());
        }
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
