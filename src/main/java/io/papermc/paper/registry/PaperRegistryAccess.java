package io.papermc.paper.registry;

import io.papermc.paper.registry.entry.ApiRegistryEntry;
import io.papermc.paper.registry.entry.RegistryEntry;
import io.papermc.paper.registry.legacy.DelayedRegistry;
import io.papermc.paper.registry.legacy.DelayedRegistryEntry;
import io.papermc.paper.registry.legacy.LegacyRegistryIdentifiers;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.Objects.requireNonNull;

@DefaultQualifier(NonNull.class)
public class PaperRegistryAccess implements RegistryAccess {

    // We store the API registries in a memoized supplier, so they can be created on-demand.
    // These suppliers are added to this map right after the instance of nms.Registry is created before it is loaded.
    // We want to do registration there, so we have access to the nms.Registry instance in order to wrap it in a CraftRegistry instance.
    // The memoized Supplier is needed because we *can't* instantiate any CraftRegistry class until **all** the BuiltInRegistries have been added
    // to this map because that would class-load org.bukkit.Registry which would query this map.
    private final Map<RegistryKey<?>, RegistryHolder<?>> registries = new ConcurrentHashMap<>(); // is "identity" because RegistryKey overrides equals and hashCode

    public static PaperRegistryAccess instance() {
        return (PaperRegistryAccess) RegistryAccessHolder.INSTANCE.orElseThrow(() -> new IllegalStateException("No RegistryAccess implementation found"));
    }

    @VisibleForTesting
    public Set<RegistryKey<?>> getLoadedServerBackedRegistries() {
        return this.registries.keySet().stream().filter(registryHolder -> !(PaperRegistries.getEntry(registryHolder) instanceof ApiRegistryEntry)).collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("unchecked")
    @Deprecated(forRemoval = true)
    @Override
    public <T extends Keyed> @Nullable Registry<T> getRegistry(final Class<T> type) {
        final RegistryKey<T> registryKey;
        final @Nullable RegistryEntry<?, T> entry;
        registryKey = requireNonNull(byType(type), () -> type + " is not a valid registry type");
        entry = PaperRegistries.getEntry(registryKey);
        final @Nullable RegistryHolder<T> registry = (RegistryHolder<T>) this.registries.get(registryKey);
        if (registry != null) {
            // if the registry exists, return right away. Since this is the "legacy" method, we return DelayedRegistry
            // for the non-builtin Registry instances stored as fields in Registry.
            return registry.get();
        } else if (entry instanceof DelayedRegistryEntry<?, T>) {
            // if the registry doesn't exist and the entry is marked as "delayed", we create a registry holder that is empty
            // which will later be filled with the actual registry. This is so the fields on org.bukkit.Registry can be populated with
            // registries that don't exist at the time org.bukkit.Registry is statically initialized.
            final RegistryHolder<T> delayedHolder = new RegistryHolder.Delayed<>();
            this.registries.put(registryKey, delayedHolder);
            return delayedHolder.get();
        } else {
            // if the registry doesn't exist yet or doesn't have a delayed entry, just return null
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Keyed> Registry<T> getRegistry(final RegistryKey<T> key) {
        if (PaperRegistries.getEntry(key) == null) {
            throw new NoSuchElementException(key + " is not a valid registry key");
        }
        final @Nullable RegistryHolder<T> registryHolder = (RegistryHolder<T>) this.registries.get(key);
        if (registryHolder == null) {
            throw new IllegalArgumentException(key + " points to a registry that is not available yet");
        }
        // since this is the getRegistry method that uses the modern RegistryKey, we unwrap any DelayedRegistry instances
        // that might be returned here. I don't think reference equality is required when doing getRegistry(RegistryKey.WOLF_VARIANT) == Registry.WOLF_VARIANT
        return possiblyUnwrap(registryHolder.get());
    }

    private static <T extends Keyed> Registry<T> possiblyUnwrap(final Registry<T> registry) {
        if (registry instanceof final DelayedRegistry<T, ?> delayedRegistry) { // if not coming from legacy, unwrap the delayed registry
            return delayedRegistry.delegate();
        }
        return registry;
    }

    public <M> void registerReloadableRegistry(final ResourceKey<? extends net.minecraft.core.Registry<M>> resourceKey, final net.minecraft.core.Registry<M> registry) {
        this.registerRegistry(resourceKey, registry, true);
    }

    public <M> void registerRegistry(final ResourceKey<? extends net.minecraft.core.Registry<M>> resourceKey, final net.minecraft.core.Registry<M> registry) {
        this.registerRegistry(resourceKey, registry, false);
    }

    @SuppressWarnings("unchecked") // this method should be called right after any new MappedRegistry instances are created to later be used by the server.
    private <M, B extends Keyed, R extends Registry<B>> void registerRegistry(final ResourceKey<? extends net.minecraft.core.Registry<M>> resourceKey, final net.minecraft.core.Registry<M> registry, final boolean replace) {
        final @Nullable RegistryEntry<M, B> entry = PaperRegistries.getEntry(resourceKey);
        if (entry == null) { // skip registries that don't have API entries
            return;
        }
        final @Nullable RegistryHolder<B> registryHolder = (RegistryHolder<B>) this.registries.get(entry.apiKey());
        if (registryHolder == null || replace) {
            // if the holder doesn't exist yet, or is marked as "replaceable", put it in the map.
            this.registries.put(entry.apiKey(), entry.createRegistryHolder(registry));
        } else {
            if (registryHolder instanceof RegistryHolder.Delayed<?, ?> && entry instanceof final DelayedRegistryEntry<M, B> delayedEntry) {
                // if the registry holder is delayed, and the entry is marked as "delayed", then load the holder with the CraftRegistry instance that wraps the actual nms Registry.
                ((RegistryHolder.Delayed<B, R>) registryHolder).loadFrom(delayedEntry, registry);
            } else {
                throw new IllegalArgumentException(resourceKey + " has already been created");
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    @VisibleForTesting
    public static <T extends Keyed> @Nullable RegistryKey<T> byType(final Class<T> type) {
        return (RegistryKey<T>) LegacyRegistryIdentifiers.CLASS_TO_KEY_MAP.get(type);
    }
}
