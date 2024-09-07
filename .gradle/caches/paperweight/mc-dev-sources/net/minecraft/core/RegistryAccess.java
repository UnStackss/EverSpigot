package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

public interface RegistryAccess extends HolderLookup.Provider {
    Logger LOGGER = LogUtils.getLogger();
    RegistryAccess.Frozen EMPTY = new RegistryAccess.ImmutableRegistryAccess(Map.of()).freeze();

    <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key);

    @Override
    default <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
        return this.registry(registryRef).map(Registry::asLookup);
    }

    default <E> Registry<E> registryOrThrow(ResourceKey<? extends Registry<? extends E>> key) {
        return this.registry(key).orElseThrow(() -> new IllegalStateException("Missing registry: " + key));
    }

    Stream<RegistryAccess.RegistryEntry<?>> registries();

    @Override
    default Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
        return this.registries().map(RegistryAccess.RegistryEntry::key);
    }

    static RegistryAccess.Frozen fromRegistryOfRegistries(Registry<? extends Registry<?>> registries) {
        return new RegistryAccess.Frozen() {
            @Override
            public <T> Optional<Registry<T>> registry(ResourceKey<? extends Registry<? extends T>> key) {
                Registry<Registry<T>> registry = (Registry<Registry<T>>)registries;
                return registry.getOptional((ResourceKey<Registry<T>>)key);
            }

            @Override
            public Stream<RegistryAccess.RegistryEntry<?>> registries() {
                return registries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
            }

            @Override
            public RegistryAccess.Frozen freeze() {
                return this;
            }
        };
    }

    default RegistryAccess.Frozen freeze() {
        class FrozenAccess extends RegistryAccess.ImmutableRegistryAccess implements RegistryAccess.Frozen {
            protected FrozenAccess(final Stream<RegistryAccess.RegistryEntry<?>> entryStream) {
                super(entryStream);
            }
        }

        return new FrozenAccess(this.registries().map(RegistryAccess.RegistryEntry::freeze));
    }

    default Lifecycle allRegistriesLifecycle() {
        return this.registries().map(entry -> entry.value.registryLifecycle()).reduce(Lifecycle.stable(), Lifecycle::add);
    }

    public interface Frozen extends RegistryAccess {
    }

    public static class ImmutableRegistryAccess implements RegistryAccess {
        private final Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries;

        public ImmutableRegistryAccess(List<? extends Registry<?>> registries) {
            this.registries = registries.stream().collect(Collectors.toUnmodifiableMap(Registry::key, registry -> registry));
        }

        public ImmutableRegistryAccess(Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries) {
            this.registries = Map.copyOf(registries);
        }

        public ImmutableRegistryAccess(Stream<RegistryAccess.RegistryEntry<?>> entryStream) {
            this.registries = entryStream.collect(ImmutableMap.toImmutableMap(RegistryAccess.RegistryEntry::key, RegistryAccess.RegistryEntry::value));
        }

        @Override
        public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key) {
            return Optional.ofNullable(this.registries.get(key)).map(registry -> (Registry<E>)registry);
        }

        @Override
        public Stream<RegistryAccess.RegistryEntry<?>> registries() {
            return this.registries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
        }
    }

    public static record RegistryEntry<T>(ResourceKey<? extends Registry<T>> key, Registry<T> value) {
        private static <T, R extends Registry<? extends T>> RegistryAccess.RegistryEntry<T> fromMapEntry(
            Entry<? extends ResourceKey<? extends Registry<?>>, R> entry
        ) {
            return fromUntyped((ResourceKey<? extends Registry<?>>)entry.getKey(), entry.getValue());
        }

        private static <T> RegistryAccess.RegistryEntry<T> fromUntyped(ResourceKey<? extends Registry<?>> key, Registry<?> value) {
            return new RegistryAccess.RegistryEntry<>((ResourceKey<? extends Registry<T>>)key, (Registry<T>)value);
        }

        private RegistryAccess.RegistryEntry<T> freeze() {
            return new RegistryAccess.RegistryEntry<>(this.key, this.value.freeze());
        }
    }
}
