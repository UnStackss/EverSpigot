package net.minecraft.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.util.ExtraCodecs;

public class RegistryOps<T> extends DelegatingOps<T> {
    public final RegistryOps.RegistryInfoLookup lookupProvider;

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, HolderLookup.Provider wrapperLookup) {
        return create(delegate, new RegistryOps.HolderLookupAdapter(wrapperLookup));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup registryInfoGetter) {
        return new RegistryOps<>(delegate, registryInfoGetter);
    }

    public static <T> Dynamic<T> injectRegistryContext(Dynamic<T> dynamic, HolderLookup.Provider registryLookup) {
        return new Dynamic<>(registryLookup.createSerializationContext(dynamic.getOps()), dynamic.getValue());
    }

    private RegistryOps(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup registryInfoGetter) {
        super(delegate);
        this.lookupProvider = registryInfoGetter;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> delegate) {
        return (RegistryOps<U>)(delegate == this.delegate ? this : new RegistryOps<>(delegate, this.lookupProvider));
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> registryRef) {
        return this.lookupProvider.lookup(registryRef).map(RegistryOps.RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> registryRef) {
        return this.lookupProvider.lookup(registryRef).map(RegistryOps.RegistryInfo::getter);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            RegistryOps<?> registryOps = (RegistryOps<?>)object;
            return this.delegate.equals(registryOps.delegate) && this.lookupProvider.equals(registryOps.lookupProvider);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode() * 31 + this.lookupProvider.hashCode();
    }

    public static <E, O> RecordCodecBuilder<O, HolderGetter<E>> retrieveGetter(ResourceKey<? extends Registry<? extends E>> registryRef) {
        return ExtraCodecs.retrieveContext(
                ops -> ops instanceof RegistryOps<?> registryOps
                        ? registryOps.lookupProvider
                            .lookup(registryRef)
                            .map(info -> DataResult.success(info.getter(), info.elementsLifecycle()))
                            .orElseGet(() -> DataResult.error(() -> "Unknown registry: " + registryRef))
                        : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(object -> null);
    }

    public static <E, O> RecordCodecBuilder<O, Holder.Reference<E>> retrieveElement(ResourceKey<E> key) {
        ResourceKey<? extends Registry<E>> resourceKey = ResourceKey.createRegistryKey(key.registry());
        return ExtraCodecs.retrieveContext(
                ops -> ops instanceof RegistryOps<?> registryOps
                        ? registryOps.lookupProvider
                            .lookup(resourceKey)
                            .flatMap(info -> info.getter().get(key))
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Can't find value: " + key))
                        : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(object -> null);
    }

    public static final class HolderLookupAdapter implements RegistryOps.RegistryInfoLookup {
        private final HolderLookup.Provider lookupProvider;
        private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap<>();

        public HolderLookupAdapter(HolderLookup.Provider registriesLookup) {
            this.lookupProvider = registriesLookup;
        }

        @Override
        public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryRef) {
            return (Optional<RegistryOps.RegistryInfo<E>>)this.lookups.computeIfAbsent(registryRef, this::createLookup);
        }

        private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> registryRef) {
            return this.lookupProvider.lookup(registryRef).map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else {
                if (object instanceof RegistryOps.HolderLookupAdapter holderLookupAdapter && this.lookupProvider.equals(holderLookupAdapter.lookupProvider)) {
                    return true;
                }

                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.lookupProvider.hashCode();
        }
    }

    public static record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {
        public static <T> RegistryOps.RegistryInfo<T> fromRegistryLookup(HolderLookup.RegistryLookup<T> wrapper) {
            return new RegistryOps.RegistryInfo<>(wrapper, wrapper, wrapper.registryLifecycle());
        }
    }

    public interface RegistryInfoLookup {
        <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef);
    }
}
