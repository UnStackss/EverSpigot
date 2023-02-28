package io.papermc.paper.registry.event.type;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.PrioritizableLifecycleEventType;
import io.papermc.paper.registry.RegistryBuilder;
import io.papermc.paper.registry.event.RegistryEvent;
import io.papermc.paper.registry.event.RegistryEventProvider;

public final class RegistryLifecycleEventType<T, B extends RegistryBuilder<T>, E extends RegistryEvent<T>> extends PrioritizableLifecycleEventType.Simple<BootstrapContext, E> {

    public RegistryLifecycleEventType(final RegistryEventProvider<T, B> type, final String eventName) {
        super(type.registryKey() + " / " + eventName, BootstrapContext.class);
    }
}
