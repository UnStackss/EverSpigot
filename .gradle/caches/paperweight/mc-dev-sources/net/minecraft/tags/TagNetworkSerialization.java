package net.minecraft.tags;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;

public class TagNetworkSerialization {
    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializeTagsToNetwork(
        LayeredRegistryAccess<RegistryLayer> dynamicRegistryManager
    ) {
        return RegistrySynchronization.networkSafeRegistries(dynamicRegistryManager)
            .map(registry -> Pair.of(registry.key(), serializeToNetwork(registry.value())))
            .filter(pair -> pair.getSecond().size() > 0)
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private static <T> TagNetworkSerialization.NetworkPayload serializeToNetwork(Registry<T> registry) {
        Map<ResourceLocation, IntList> map = new HashMap<>();
        registry.getTags().forEach(pair -> {
            HolderSet<T> holderSet = pair.getSecond();
            IntList intList = new IntArrayList(holderSet.size());

            for (Holder<T> holder : holderSet) {
                if (holder.kind() != Holder.Kind.REFERENCE) {
                    throw new IllegalStateException("Can't serialize unregistered value " + holder);
                }

                intList.add(registry.getId(holder.value()));
            }

            map.put(pair.getFirst().location(), intList);
        });
        return new TagNetworkSerialization.NetworkPayload(map);
    }

    static <T> void deserializeTagsFromNetwork(
        ResourceKey<? extends Registry<T>> registryKey,
        Registry<T> registry,
        TagNetworkSerialization.NetworkPayload serialized,
        TagNetworkSerialization.TagOutput<T> loader
    ) {
        serialized.tags.forEach((tagId, rawIds) -> {
            TagKey<T> tagKey = TagKey.create(registryKey, tagId);
            List<Holder<T>> list = rawIds.intStream().mapToObj(registry::getHolder).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
            loader.accept(tagKey, list);
        });
    }

    public static final class NetworkPayload {
        final Map<ResourceLocation, IntList> tags;

        NetworkPayload(Map<ResourceLocation, IntList> contents) {
            this.tags = contents;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeMap(this.tags, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeIntIdList);
        }

        public static TagNetworkSerialization.NetworkPayload read(FriendlyByteBuf buf) {
            return new TagNetworkSerialization.NetworkPayload(buf.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readIntIdList));
        }

        public int size() {
            return this.tags.size();
        }

        public <T> void applyToRegistry(Registry<T> registry) {
            if (this.size() != 0) {
                Map<TagKey<T>, List<Holder<T>>> map = new HashMap<>(this.size());
                TagNetworkSerialization.deserializeTagsFromNetwork(registry.key(), registry, this, map::put);
                registry.bindTags(map);
            }
        }
    }

    @FunctionalInterface
    public interface TagOutput<T> {
        void accept(TagKey<T> tag, List<Holder<T>> entries);
    }
}
