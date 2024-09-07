package net.minecraft.core;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public interface HolderGetter<T> {
    Optional<Holder.Reference<T>> get(ResourceKey<T> key);

    default Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
        return this.get(key).orElseThrow(() -> new IllegalStateException("Missing element " + key));
    }

    Optional<HolderSet.Named<T>> get(TagKey<T> tag);

    default HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
        return this.get(tag).orElseThrow(() -> new IllegalStateException("Missing tag " + tag));
    }

    public interface Provider {
        <T> Optional<HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef);

        default <T> HolderGetter<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> registryRef) {
            return this.lookup(registryRef).orElseThrow(() -> new IllegalStateException("Registry " + registryRef.location() + " not found"));
        }

        default <T> Optional<Holder.Reference<T>> get(ResourceKey<? extends Registry<? extends T>> registryRef, ResourceKey<T> key) {
            return this.lookup(registryRef).flatMap(registryEntryLookup -> registryEntryLookup.get(key));
        }
    }
}
