package io.papermc.paper.registry.entry;

import io.papermc.paper.registry.RegistryHolder;
import io.papermc.paper.registry.RegistryKey;
import java.util.function.BiFunction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.util.ApiVersion;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public class CraftRegistryEntry<M, B extends Keyed> extends BaseRegistryEntry<M, B> { // TODO remove Keyed

    private static final BiFunction<NamespacedKey, ApiVersion, NamespacedKey> EMPTY = (namespacedKey, apiVersion) -> namespacedKey;

    protected final Class<?> classToPreload;
    protected final BiFunction<NamespacedKey, M, B> minecraftToBukkit;
    protected BiFunction<NamespacedKey, ApiVersion, NamespacedKey> updater = EMPTY;

    protected CraftRegistryEntry(
        final ResourceKey<? extends Registry<M>> mcKey,
        final RegistryKey<B> apiKey,
        final Class<?> classToPreload,
        final BiFunction<NamespacedKey, M, B> minecraftToBukkit
    ) {
        super(mcKey, apiKey);
        this.classToPreload = classToPreload;
        this.minecraftToBukkit = minecraftToBukkit;
    }

    @Override
    public RegistryEntry<M, B> withSerializationUpdater(final BiFunction<NamespacedKey, ApiVersion, NamespacedKey> updater) {
        this.updater = updater;
        return this;
    }

    @Override
    public RegistryHolder<B> createRegistryHolder(final Registry<M> nmsRegistry) {
        return new RegistryHolder.Memoized<>(() -> this.createApiRegistry(nmsRegistry));
    }

    private CraftRegistry<B, M> createApiRegistry(final Registry<M> nmsRegistry) {
        return new CraftRegistry<>(this.classToPreload, nmsRegistry, this.minecraftToBukkit, this.updater);
    }
}
