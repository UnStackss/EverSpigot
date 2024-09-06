package io.papermc.paper.registry.event;

import io.papermc.paper.plugin.lifecycle.event.PaperLifecycleEvent;
import io.papermc.paper.registry.PaperRegistries;
import io.papermc.paper.registry.RegistryBuilder;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.util.Conversions;
import io.papermc.paper.registry.set.NamedRegistryKeySetImpl;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.RegistryOps;
import org.bukkit.Keyed;
import org.checkerframework.checker.nullness.qual.NonNull;

public record RegistryFreezeEventImpl<T, B extends RegistryBuilder<T>>(
    RegistryKey<T> registryKey,
    WritableRegistry<T, B> registry,
    Conversions conversions
) implements RegistryFreezeEvent<T, B>, PaperLifecycleEvent {

    @Override
    public @NonNull <V extends Keyed> Tag<V> getOrCreateTag(final TagKey<V> tagKey) {
        final RegistryOps.RegistryInfo<Object> registryInfo = this.conversions.lookup().lookup(PaperRegistries.registryToNms(tagKey.registryKey())).orElseThrow();
        final HolderSet.Named<?> tagSet = registryInfo.getter().getOrThrow(PaperRegistries.toNms(tagKey));
        return new NamedRegistryKeySetImpl<>(tagKey, tagSet);
    }
}
