package io.papermc.paper.registry.event;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import io.papermc.paper.registry.RegistryBuilder;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public final class RegistryEventMap {

    private final Map<RegistryKey<?>, LifecycleEventType<BootstrapContext, ? extends RegistryEvent<?>, ?>> hooks = new HashMap<>();
    private final String name;

    public RegistryEventMap(final String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public <T, B extends RegistryBuilder<T>, E extends RegistryEvent<T>, ET extends LifecycleEventType<BootstrapContext, E, ?>> ET getOrCreate(final RegistryEventProvider<T, B> type, final BiFunction<? super RegistryEventProvider<T, B>, ? super String, ET> eventTypeCreator) {
        final ET registerHook;
        if (this.hooks.containsKey(type.registryKey())) {
            registerHook = (ET) this.hooks.get(type.registryKey());
        } else {
            registerHook = eventTypeCreator.apply(type, this.name);
            LifecycleEventRunner.INSTANCE.addEventType(registerHook);
            this.hooks.put(type.registryKey(), registerHook);
        }
        return registerHook;
    }

    @SuppressWarnings("unchecked")
    public <T, E extends RegistryEvent<T>> LifecycleEventType<BootstrapContext, E, ?> getHook(final RegistryKey<T> registryKey) {
        return (LifecycleEventType<BootstrapContext, E, ?>) Objects.requireNonNull(this.hooks.get(registryKey), "No hook for " + registryKey);
    }

    public boolean hasHooks(final RegistryKey<?> registryKey) {
        return this.hooks.containsKey(registryKey);
    }

}
