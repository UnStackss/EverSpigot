package io.papermc.paper.registry;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Lifecycle;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.entrypoint.Entrypoint;
import io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import io.papermc.paper.registry.data.util.Conversions;
import io.papermc.paper.registry.entry.RegistryEntry;
import io.papermc.paper.registry.entry.RegistryEntryInfo;
import io.papermc.paper.registry.event.RegistryEntryAddEventImpl;
import io.papermc.paper.registry.event.RegistryEventMap;
import io.papermc.paper.registry.event.RegistryEventProvider;
import io.papermc.paper.registry.event.RegistryFreezeEvent;
import io.papermc.paper.registry.event.RegistryFreezeEventImpl;
import io.papermc.paper.registry.event.type.RegistryEntryAddEventType;
import io.papermc.paper.registry.event.type.RegistryEntryAddEventTypeImpl;
import io.papermc.paper.registry.event.type.RegistryLifecycleEventType;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.intellij.lang.annotations.Subst;

public final class PaperRegistryListenerManager {

    public static final PaperRegistryListenerManager INSTANCE = new PaperRegistryListenerManager();

    public final RegistryEventMap valueAddHooks = new RegistryEventMap("value add");
    public final RegistryEventMap freezeHooks = new RegistryEventMap("freeze");

    private PaperRegistryListenerManager() {
    }

    /**
     * For {@link Registry#register(Registry, String, Object)}
     */
    public <M> M registerWithListeners(final Registry<M> registry, final String id, final M nms) {
        return this.registerWithListeners(registry, ResourceLocation.withDefaultNamespace(id), nms);
    }

    /**
     * For {@link Registry#register(Registry, ResourceLocation, Object)}
     */
    public <M> M registerWithListeners(final Registry<M> registry, final ResourceLocation loc, final M nms) {
        return this.registerWithListeners(registry, ResourceKey.create(registry.key(), loc), nms);
    }

    /**
     * For {@link Registry#register(Registry, ResourceKey, Object)}
     */
    public <M> M registerWithListeners(final Registry<M> registry, final ResourceKey<M> key, final M nms) {
        return this.registerWithListeners(registry, key, nms, RegistrationInfo.BUILT_IN, PaperRegistryListenerManager::registerWithInstance, BuiltInRegistries.BUILT_IN_CONVERSIONS);
    }

    /**
     * For {@link Registry#registerForHolder(Registry, ResourceLocation, Object)}
     */
    public <M> Holder.Reference<M> registerForHolderWithListeners(final Registry<M> registry, final ResourceLocation loc, final M nms) {
        return this.registerForHolderWithListeners(registry, ResourceKey.create(registry.key(), loc), nms);
    }

    /**
     * For {@link Registry#registerForHolder(Registry, ResourceKey, Object)}
     */
    public <M> Holder.Reference<M> registerForHolderWithListeners(final Registry<M> registry, final ResourceKey<M> key, final M nms) {
        return this.registerWithListeners(registry, key, nms, RegistrationInfo.BUILT_IN, WritableRegistry::register, BuiltInRegistries.BUILT_IN_CONVERSIONS);
    }

    public <M> void registerWithListeners(
        final Registry<M> registry,
        final ResourceKey<M> key,
        final M nms,
        final RegistrationInfo registrationInfo,
        final Conversions conversions
    ) {
        this.registerWithListeners(registry, key, nms, registrationInfo, WritableRegistry::register, conversions);
    }

    // TODO remove Keyed
    public <M, T extends org.bukkit.Keyed, B extends PaperRegistryBuilder<M, T>, R> R registerWithListeners(
        final Registry<M> registry,
        final ResourceKey<M> key,
        final M nms,
        final RegistrationInfo registrationInfo,
        final RegisterMethod<M, R> registerMethod,
        final Conversions conversions
    ) {
        Preconditions.checkState(LaunchEntryPointHandler.INSTANCE.hasEntered(Entrypoint.BOOTSTRAPPER), registry.key() + " tried to run modification listeners before bootstrappers have been called"); // verify that bootstrappers have been called
        final @Nullable RegistryEntryInfo<M, T> entry = PaperRegistries.getEntry(registry.key());
        if (!RegistryEntry.Modifiable.isModifiable(entry) || !this.valueAddHooks.hasHooks(entry.apiKey())) {
            return registerMethod.register((WritableRegistry<M>) registry, key, nms, registrationInfo);
        }
        final RegistryEntry.Modifiable<M, T, B> modifiableEntry = RegistryEntry.Modifiable.asModifiable(entry);
        @SuppressWarnings("PatternValidation") final TypedKey<T> typedKey = TypedKey.create(entry.apiKey(), Key.key(key.location().getNamespace(), key.location().getPath()));
        final B builder = modifiableEntry.fillBuilder(conversions, typedKey, nms);
        return this.registerWithListeners(registry, modifiableEntry, key, nms, builder, registrationInfo, registerMethod, conversions);
    }

    <M, T extends org.bukkit.Keyed, B extends PaperRegistryBuilder<M, T>> void registerWithListeners( // TODO remove Keyed
        final WritableRegistry<M> registry,
        final RegistryEntryInfo<M, T> entry,
        final ResourceKey<M> key,
        final B builder,
        final RegistrationInfo registrationInfo,
        final Conversions conversions
    ) {
        if (!RegistryEntry.Modifiable.isModifiable(entry) || !this.valueAddHooks.hasHooks(entry.apiKey())) {
            registry.register(key, builder.build(), registrationInfo);
            return;
        }
        this.registerWithListeners(registry, RegistryEntry.Modifiable.asModifiable(entry), key, null, builder, registrationInfo, WritableRegistry::register, conversions);
    }

    public <M, T extends org.bukkit.Keyed, B extends PaperRegistryBuilder<M, T>, R> R registerWithListeners( // TODO remove Keyed
        final Registry<M> registry,
        final RegistryEntry.Modifiable<M, T, B> entry,
        final ResourceKey<M> key,
        final @Nullable M oldNms,
        final B builder,
        RegistrationInfo registrationInfo,
        final RegisterMethod<M, R> registerMethod,
        final Conversions conversions
    ) {
        @Subst("namespace:key") final ResourceLocation beingAdded = key.location();
        @SuppressWarnings("PatternValidation") final TypedKey<T> typedKey = TypedKey.create(entry.apiKey(), Key.key(beingAdded.getNamespace(), beingAdded.getPath()));
        final RegistryEntryAddEventImpl<T, B> event = entry.createEntryAddEvent(typedKey, builder, conversions);
        LifecycleEventRunner.INSTANCE.callEvent(this.valueAddHooks.getHook(entry.apiKey()), event);
        if (oldNms != null) {
            ((MappedRegistry<M>) registry).clearIntrusiveHolder(oldNms);
        }
        final M newNms = event.builder().build();
        if (oldNms != null && !newNms.equals(oldNms)) {
            registrationInfo = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());
        }
        return registerMethod.register((WritableRegistry<M>) registry, key, newNms, registrationInfo);
    }

    private static <M> M registerWithInstance(final WritableRegistry<M> writableRegistry, final ResourceKey<M> key, final M value, final RegistrationInfo registrationInfo) {
        writableRegistry.register(key, value, registrationInfo);
        return value;
    }

    @FunctionalInterface
    public interface RegisterMethod<M, R> {

        R register(WritableRegistry<M> writableRegistry, ResourceKey<M> key, M value, RegistrationInfo registrationInfo);
    }

    public <M, T extends org.bukkit.Keyed, B extends PaperRegistryBuilder<M, T>> void runFreezeListeners(final ResourceKey<? extends Registry<M>> resourceKey, final Conversions conversions) {
        final @Nullable RegistryEntryInfo<M, T> entry = PaperRegistries.getEntry(resourceKey);
        if (!RegistryEntry.Addable.isAddable(entry) || !this.freezeHooks.hasHooks(entry.apiKey())) {
            return;
        }
        final RegistryEntry.Addable<M, T, B> writableEntry = RegistryEntry.Addable.asAddable(entry);
        final WritableCraftRegistry<M, T, B> writableRegistry = PaperRegistryAccess.instance().getWritableRegistry(entry.apiKey());
        final RegistryFreezeEventImpl<T, B> event = writableEntry.createFreezeEvent(writableRegistry, conversions);
        LifecycleEventRunner.INSTANCE.callEvent(this.freezeHooks.getHook(entry.apiKey()), event);
    }

    public <T, B extends RegistryBuilder<T>> RegistryEntryAddEventType<T, B> getRegistryValueAddEventType(final RegistryEventProvider<T, B> type) {
        if (!RegistryEntry.Modifiable.isModifiable(PaperRegistries.getEntry(type.registryKey()))) {
            throw new IllegalArgumentException(type.registryKey() + " does not support RegistryEntryAddEvent");
        }
        return this.valueAddHooks.getOrCreate(type, RegistryEntryAddEventTypeImpl::new);
    }

    public <T, B extends RegistryBuilder<T>> LifecycleEventType.Prioritizable<BootstrapContext, RegistryFreezeEvent<T, B>> getRegistryFreezeEventType(final RegistryEventProvider<T, B> type) {
        if (!RegistryEntry.Addable.isAddable(PaperRegistries.getEntry(type.registryKey()))) {
            throw new IllegalArgumentException(type.registryKey() + " does not support RegistryFreezeEvent");
        }
        return this.freezeHooks.getOrCreate(type, RegistryLifecycleEventType::new);
    }
}
