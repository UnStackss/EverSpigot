package net.minecraft.core;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.repository.KnownPack;

public class RegistrySynchronization {
    public static final Set<ResourceKey<? extends Registry<?>>> NETWORKABLE_REGISTRIES = RegistryDataLoader.SYNCHRONIZED_REGISTRIES
        .stream()
        .map(RegistryDataLoader.RegistryData::key)
        .collect(Collectors.toUnmodifiableSet());

    public static void packRegistries(
        DynamicOps<Tag> nbtOps,
        RegistryAccess registryManager,
        Set<KnownPack> knownPacks,
        BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> callback
    ) {
        RegistryDataLoader.SYNCHRONIZED_REGISTRIES
            .forEach(registry -> packRegistry(nbtOps, (RegistryDataLoader.RegistryData<?>)registry, registryManager, knownPacks, callback));
    }

    private static <T> void packRegistry(
        DynamicOps<Tag> nbtOps,
        RegistryDataLoader.RegistryData<T> entry,
        RegistryAccess registryManager,
        Set<KnownPack> knownPacks,
        BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> callback
    ) {
        registryManager.registry(entry.key())
            .ifPresent(
                registry -> {
                    List<RegistrySynchronization.PackedRegistryEntry> list = new ArrayList<>(registry.size());
                    registry.holders()
                        .forEach(
                            registryEntry -> {
                                boolean bl = registry.registrationInfo(registryEntry.key())
                                    .flatMap(RegistrationInfo::knownPackInfo)
                                    .filter(knownPacks::contains)
                                    .isPresent();
                                Optional<Tag> optional;
                                if (bl) {
                                    optional = Optional.empty();
                                } else {
                                    Tag tag = entry.elementCodec()
                                        .encodeStart(nbtOps, registryEntry.value())
                                        .getOrThrow(error -> new IllegalArgumentException("Failed to serialize " + registryEntry.key() + ": " + error));
                                    optional = Optional.of(tag);
                                }

                                list.add(new RegistrySynchronization.PackedRegistryEntry(registryEntry.key().location(), optional));
                            }
                        );
                    callback.accept(registry.key(), list);
                }
            );
    }

    private static Stream<RegistryAccess.RegistryEntry<?>> ownedNetworkableRegistries(RegistryAccess dynamicRegistryManager) {
        return dynamicRegistryManager.registries().filter(registry -> NETWORKABLE_REGISTRIES.contains(registry.key()));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkedRegistries(LayeredRegistryAccess<RegistryLayer> combinedRegistries) {
        return ownedNetworkableRegistries(combinedRegistries.getAccessFrom(RegistryLayer.WORLDGEN));
    }

    public static Stream<RegistryAccess.RegistryEntry<?>> networkSafeRegistries(LayeredRegistryAccess<RegistryLayer> combinedRegistries) {
        Stream<RegistryAccess.RegistryEntry<?>> stream = combinedRegistries.getLayer(RegistryLayer.STATIC).registries();
        Stream<RegistryAccess.RegistryEntry<?>> stream2 = networkedRegistries(combinedRegistries);
        return Stream.concat(stream2, stream);
    }

    public static record PackedRegistryEntry(ResourceLocation id, Optional<Tag> data) {
        public static final StreamCodec<ByteBuf, RegistrySynchronization.PackedRegistryEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            RegistrySynchronization.PackedRegistryEntry::id,
            ByteBufCodecs.TAG.apply(ByteBufCodecs::optional),
            RegistrySynchronization.PackedRegistryEntry::data,
            RegistrySynchronization.PackedRegistryEntry::new
        );
    }
}
