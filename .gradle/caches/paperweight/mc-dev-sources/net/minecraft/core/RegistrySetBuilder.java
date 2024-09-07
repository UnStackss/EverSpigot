package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.apache.commons.lang3.mutable.MutableObject;

public class RegistrySetBuilder {
    private final List<RegistrySetBuilder.RegistryStub<?>> entries = new ArrayList<>();

    static <T> HolderGetter<T> wrapContextLookup(HolderLookup.RegistryLookup<T> wrapper) {
        return new RegistrySetBuilder.EmptyTagLookup<T>(wrapper) {
            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
                return wrapper.get(key);
            }
        };
    }

    static <T> HolderLookup.RegistryLookup<T> lookupFromMap(
        ResourceKey<? extends Registry<? extends T>> registryRef, Lifecycle lifecycle, HolderOwner<T> owner, Map<ResourceKey<T>, Holder.Reference<T>> entries
    ) {
        return new RegistrySetBuilder.EmptyTagRegistryLookup<T>(owner) {
            @Override
            public ResourceKey<? extends Registry<? extends T>> key() {
                return registryRef;
            }

            @Override
            public Lifecycle registryLifecycle() {
                return lifecycle;
            }

            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
                return Optional.ofNullable(entries.get(key));
            }

            @Override
            public Stream<Holder.Reference<T>> listElements() {
                return entries.values().stream();
            }
        };
    }

    public <T> RegistrySetBuilder add(
        ResourceKey<? extends Registry<T>> registryRef, Lifecycle lifecycle, RegistrySetBuilder.RegistryBootstrap<T> bootstrapFunction
    ) {
        this.entries.add(new RegistrySetBuilder.RegistryStub<>(registryRef, lifecycle, bootstrapFunction));
        return this;
    }

    public <T> RegistrySetBuilder add(ResourceKey<? extends Registry<T>> registryRef, RegistrySetBuilder.RegistryBootstrap<T> bootstrapFunction) {
        return this.add(registryRef, Lifecycle.stable(), bootstrapFunction);
    }

    private RegistrySetBuilder.BuildState createState(RegistryAccess registryManager) {
        RegistrySetBuilder.BuildState buildState = RegistrySetBuilder.BuildState.create(
            registryManager, this.entries.stream().map(RegistrySetBuilder.RegistryStub::key)
        );
        this.entries.forEach(registry -> registry.apply(buildState));
        return buildState;
    }

    private static HolderLookup.Provider buildProviderWithContext(
        RegistrySetBuilder.UniversalOwner entryOwner, RegistryAccess registryManager, Stream<HolderLookup.RegistryLookup<?>> wrappers
    ) {
        record Entry<T>(HolderLookup.RegistryLookup<T> lookup, RegistryOps.RegistryInfo<T> opsInfo) {
            public static <T> Entry<T> createForContextRegistry(HolderLookup.RegistryLookup<T> wrapper) {
                return new Entry<>(new RegistrySetBuilder.EmptyTagLookupWrapper<>(wrapper, wrapper), RegistryOps.RegistryInfo.fromRegistryLookup(wrapper));
            }

            public static <T> Entry<T> createForNewRegistry(RegistrySetBuilder.UniversalOwner owner, HolderLookup.RegistryLookup<T> wrapper) {
                return new Entry<>(
                    new RegistrySetBuilder.EmptyTagLookupWrapper<>(owner.cast(), wrapper),
                    new RegistryOps.RegistryInfo<>(owner.cast(), wrapper, wrapper.registryLifecycle())
                );
            }
        }

        final Map<ResourceKey<? extends Registry<?>>, Entry<?>> map = new HashMap<>();
        registryManager.registries().forEach(registry -> map.put(registry.key(), Entry.createForContextRegistry(registry.value().asLookup())));
        wrappers.forEach(wrapper -> map.put(wrapper.key(), Entry.createForNewRegistry(entryOwner, wrapper)));
        return new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
                return map.keySet().stream();
            }

            <T> Optional<Entry<T>> getEntry(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return Optional.ofNullable((Entry<T>)map.get(registryRef));
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return this.getEntry(registryRef).map(Entry::lookup);
            }

            @Override
            public <V> RegistryOps<V> createSerializationContext(DynamicOps<V> delegate) {
                return RegistryOps.create(delegate, new RegistryOps.RegistryInfoLookup() {
                    @Override
                    public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                        return getEntry(registryRef).map(Entry::opsInfo);
                    }
                });
            }
        };
    }

    public HolderLookup.Provider build(RegistryAccess registryManager) {
        RegistrySetBuilder.BuildState buildState = this.createState(registryManager);
        Stream<HolderLookup.RegistryLookup<?>> stream = this.entries
            .stream()
            .map(info -> info.collectRegisteredValues(buildState).buildAsLookup(buildState.owner));
        HolderLookup.Provider provider = buildProviderWithContext(buildState.owner, registryManager, stream);
        buildState.reportNotCollectedHolders();
        buildState.reportUnclaimedRegisteredValues();
        buildState.throwOnError();
        return provider;
    }

    private HolderLookup.Provider createLazyFullPatchedRegistries(
        RegistryAccess registryManager,
        HolderLookup.Provider base,
        Cloner.Factory cloneableRegistries,
        Map<ResourceKey<? extends Registry<?>>, RegistrySetBuilder.RegistryContents<?>> initializedRegistries,
        HolderLookup.Provider patches
    ) {
        RegistrySetBuilder.UniversalOwner universalOwner = new RegistrySetBuilder.UniversalOwner();
        MutableObject<HolderLookup.Provider> mutableObject = new MutableObject<>();
        List<HolderLookup.RegistryLookup<?>> list = initializedRegistries.keySet()
            .stream()
            .map(
                registryRef -> this.createLazyFullPatchedRegistries(
                        universalOwner, cloneableRegistries, (ResourceKey<? extends Registry<? extends Object>>)registryRef, patches, base, mutableObject
                    )
            )
            .collect(Collectors.toUnmodifiableList());
        HolderLookup.Provider provider = buildProviderWithContext(universalOwner, registryManager, list.stream());
        mutableObject.setValue(provider);
        return provider;
    }

    private <T> HolderLookup.RegistryLookup<T> createLazyFullPatchedRegistries(
        HolderOwner<T> owner,
        Cloner.Factory cloneableRegistries,
        ResourceKey<? extends Registry<? extends T>> registryRef,
        HolderLookup.Provider patches,
        HolderLookup.Provider base,
        MutableObject<HolderLookup.Provider> lazyWrapper
    ) {
        Cloner<T> cloner = cloneableRegistries.cloner(registryRef);
        if (cloner == null) {
            throw new NullPointerException("No cloner for " + registryRef.location());
        } else {
            Map<ResourceKey<T>, Holder.Reference<T>> map = new HashMap<>();
            HolderLookup.RegistryLookup<T> registryLookup = patches.lookupOrThrow(registryRef);
            registryLookup.listElements().forEach(entry -> {
                ResourceKey<T> resourceKey = entry.key();
                RegistrySetBuilder.LazyHolder<T> lazyHolder = new RegistrySetBuilder.LazyHolder<>(owner, resourceKey);
                lazyHolder.supplier = () -> cloner.clone((T)entry.value(), patches, lazyWrapper.getValue());
                map.put(resourceKey, lazyHolder);
            });
            HolderLookup.RegistryLookup<T> registryLookup2 = base.lookupOrThrow(registryRef);
            registryLookup2.listElements().forEach(entry -> {
                ResourceKey<T> resourceKey = entry.key();
                map.computeIfAbsent(resourceKey, key -> {
                    RegistrySetBuilder.LazyHolder<T> lazyHolder = new RegistrySetBuilder.LazyHolder<>(owner, resourceKey);
                    lazyHolder.supplier = () -> cloner.clone((T)entry.value(), base, lazyWrapper.getValue());
                    return lazyHolder;
                });
            });
            Lifecycle lifecycle = registryLookup.registryLifecycle().add(registryLookup2.registryLifecycle());
            return lookupFromMap(registryRef, lifecycle, owner, map);
        }
    }

    public RegistrySetBuilder.PatchedRegistries buildPatch(
        RegistryAccess baseRegistryManager, HolderLookup.Provider wrapperLookup, Cloner.Factory cloneableRegistries
    ) {
        RegistrySetBuilder.BuildState buildState = this.createState(baseRegistryManager);
        Map<ResourceKey<? extends Registry<?>>, RegistrySetBuilder.RegistryContents<?>> map = new HashMap<>();
        this.entries
            .stream()
            .map(info -> info.collectRegisteredValues(buildState))
            .forEach(registry -> map.put(registry.key, (RegistrySetBuilder.RegistryContents<?>)registry));
        Set<ResourceKey<? extends Registry<?>>> set = baseRegistryManager.listRegistries().collect(Collectors.toUnmodifiableSet());
        wrapperLookup.listRegistries()
            .filter(key -> !set.contains(key))
            .forEach(
                key -> map.putIfAbsent(
                        (ResourceKey<? extends Registry<?>>)key,
                        new RegistrySetBuilder.RegistryContents<>((ResourceKey<? extends Registry<?>>)key, Lifecycle.stable(), Map.of())
                    )
            );
        Stream<HolderLookup.RegistryLookup<?>> stream = map.values().stream().map(registry -> registry.buildAsLookup(buildState.owner));
        HolderLookup.Provider provider = buildProviderWithContext(buildState.owner, baseRegistryManager, stream);
        buildState.reportUnclaimedRegisteredValues();
        buildState.throwOnError();
        HolderLookup.Provider provider2 = this.createLazyFullPatchedRegistries(baseRegistryManager, wrapperLookup, cloneableRegistries, map, provider);
        return new RegistrySetBuilder.PatchedRegistries(provider2, provider);
    }

    static record BuildState(
        RegistrySetBuilder.UniversalOwner owner,
        RegistrySetBuilder.UniversalLookup lookup,
        Map<ResourceLocation, HolderGetter<?>> registries,
        Map<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>> registeredValues,
        List<RuntimeException> errors
    ) {
        public static RegistrySetBuilder.BuildState create(RegistryAccess dynamicRegistryManager, Stream<ResourceKey<? extends Registry<?>>> registryRefs) {
            RegistrySetBuilder.UniversalOwner universalOwner = new RegistrySetBuilder.UniversalOwner();
            List<RuntimeException> list = new ArrayList<>();
            RegistrySetBuilder.UniversalLookup universalLookup = new RegistrySetBuilder.UniversalLookup(universalOwner);
            Builder<ResourceLocation, HolderGetter<?>> builder = ImmutableMap.builder();
            dynamicRegistryManager.registries()
                .forEach(entry -> builder.put(entry.key().location(), RegistrySetBuilder.wrapContextLookup(entry.value().asLookup())));
            registryRefs.forEach(registryRef -> builder.put(registryRef.location(), universalLookup));
            return new RegistrySetBuilder.BuildState(universalOwner, universalLookup, builder.build(), new HashMap<>(), list);
        }

        public <T> BootstrapContext<T> bootstrapContext() {
            return new BootstrapContext<T>() {
                @Override
                public Holder.Reference<T> register(ResourceKey<T> key, T value, Lifecycle lifecycle) {
                    RegistrySetBuilder.RegisteredValue<?> registeredValue = BuildState.this.registeredValues
                        .put(key, new RegistrySetBuilder.RegisteredValue(value, lifecycle));
                    if (registeredValue != null) {
                        BuildState.this.errors
                            .add(new IllegalStateException("Duplicate registration for " + key + ", new=" + value + ", old=" + registeredValue.value));
                    }

                    return BuildState.this.lookup.getOrCreate(key);
                }

                @Override
                public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> registryRef) {
                    return (HolderGetter<S>)BuildState.this.registries.getOrDefault(registryRef.location(), BuildState.this.lookup);
                }
            };
        }

        public void reportUnclaimedRegisteredValues() {
            this.registeredValues.forEach((key, value) -> this.errors.add(new IllegalStateException("Orpaned value " + value.value + " for key " + key)));
        }

        public void reportNotCollectedHolders() {
            for (ResourceKey<Object> resourceKey : this.lookup.holders.keySet()) {
                this.errors.add(new IllegalStateException("Unreferenced key: " + resourceKey));
            }
        }

        public void throwOnError() {
            if (!this.errors.isEmpty()) {
                IllegalStateException illegalStateException = new IllegalStateException("Errors during registry creation");

                for (RuntimeException runtimeException : this.errors) {
                    illegalStateException.addSuppressed(runtimeException);
                }

                throw illegalStateException;
            }
        }
    }

    abstract static class EmptyTagLookup<T> implements HolderGetter<T> {
        protected final HolderOwner<T> owner;

        protected EmptyTagLookup(HolderOwner<T> entryOwner) {
            this.owner = entryOwner;
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
            return Optional.of(HolderSet.emptyNamed(this.owner, tag));
        }
    }

    static class EmptyTagLookupWrapper<T> extends RegistrySetBuilder.EmptyTagRegistryLookup<T> implements HolderLookup.RegistryLookup.Delegate<T> {
        private final HolderLookup.RegistryLookup<T> parent;

        EmptyTagLookupWrapper(HolderOwner<T> entryOwner, HolderLookup.RegistryLookup<T> base) {
            super(entryOwner);
            this.parent = base;
        }

        @Override
        public HolderLookup.RegistryLookup<T> parent() {
            return this.parent;
        }
    }

    abstract static class EmptyTagRegistryLookup<T> extends RegistrySetBuilder.EmptyTagLookup<T> implements HolderLookup.RegistryLookup<T> {
        protected EmptyTagRegistryLookup(HolderOwner<T> entryOwner) {
            super(entryOwner);
        }

        @Override
        public Stream<HolderSet.Named<T>> listTags() {
            throw new UnsupportedOperationException("Tags are not available in datagen");
        }
    }

    static class LazyHolder<T> extends Holder.Reference<T> {
        @Nullable
        Supplier<T> supplier;

        protected LazyHolder(HolderOwner<T> owner, @Nullable ResourceKey<T> key) {
            super(Holder.Reference.Type.STAND_ALONE, owner, key, null);
        }

        @Override
        protected void bindValue(T value) {
            super.bindValue(value);
            this.supplier = null;
        }

        @Override
        public T value() {
            if (this.supplier != null) {
                this.bindValue(this.supplier.get());
            }

            return super.value();
        }
    }

    public static record PatchedRegistries(HolderLookup.Provider full, HolderLookup.Provider patches) {
    }

    static record RegisteredValue<T>(T value, Lifecycle lifecycle) {
    }

    @FunctionalInterface
    public interface RegistryBootstrap<T> {
        void run(BootstrapContext<T> registerable);
    }

    static record RegistryContents<T>(
        ResourceKey<? extends Registry<? extends T>> key, Lifecycle lifecycle, Map<ResourceKey<T>, RegistrySetBuilder.ValueAndHolder<T>> values
    ) {
        public HolderLookup.RegistryLookup<T> buildAsLookup(RegistrySetBuilder.UniversalOwner anyOwner) {
            Map<ResourceKey<T>, Holder.Reference<T>> map = this.values
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, entry -> {
                    RegistrySetBuilder.ValueAndHolder<T> valueAndHolder = entry.getValue();
                    Holder.Reference<T> reference = valueAndHolder.holder().orElseGet(() -> Holder.Reference.createStandAlone(anyOwner.cast(), entry.getKey()));
                    reference.bindValue(valueAndHolder.value().value());
                    return reference;
                }));
            return RegistrySetBuilder.lookupFromMap(this.key, this.lifecycle, anyOwner.cast(), map);
        }
    }

    static record RegistryStub<T>(ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        void apply(RegistrySetBuilder.BuildState registries) {
            this.bootstrap.run(registries.bootstrapContext());
        }

        public RegistrySetBuilder.RegistryContents<T> collectRegisteredValues(RegistrySetBuilder.BuildState registries) {
            Map<ResourceKey<T>, RegistrySetBuilder.ValueAndHolder<T>> map = new HashMap<>();
            Iterator<java.util.Map.Entry<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>>> iterator = registries.registeredValues.entrySet().iterator();

            while (iterator.hasNext()) {
                java.util.Map.Entry<ResourceKey<?>, RegistrySetBuilder.RegisteredValue<?>> entry = iterator.next();
                ResourceKey<?> resourceKey = entry.getKey();
                if (resourceKey.isFor(this.key)) {
                    RegistrySetBuilder.RegisteredValue<T> registeredValue = (RegistrySetBuilder.RegisteredValue<T>)entry.getValue();
                    Holder.Reference<T> reference = (Holder.Reference<T>)registries.lookup.holders.remove(resourceKey);
                    map.put((ResourceKey<T>)resourceKey, new RegistrySetBuilder.ValueAndHolder<>(registeredValue, Optional.ofNullable(reference)));
                    iterator.remove();
                }
            }

            return new RegistrySetBuilder.RegistryContents<>(this.key, this.lifecycle, map);
        }
    }

    static class UniversalLookup extends RegistrySetBuilder.EmptyTagLookup<Object> {
        final Map<ResourceKey<Object>, Holder.Reference<Object>> holders = new HashMap<>();

        public UniversalLookup(HolderOwner<Object> entryOwner) {
            super(entryOwner);
        }

        @Override
        public Optional<Holder.Reference<Object>> get(ResourceKey<Object> key) {
            return Optional.of(this.getOrCreate(key));
        }

        <T> Holder.Reference<T> getOrCreate(ResourceKey<T> key) {
            return (Holder.Reference<T>)this.holders.computeIfAbsent(key, key2 -> Holder.Reference.createStandAlone(this.owner, (ResourceKey<Object>)key2));
        }
    }

    static class UniversalOwner implements HolderOwner<Object> {
        public <T> HolderOwner<T> cast() {
            return this;
        }
    }

    static record ValueAndHolder<T>(RegistrySetBuilder.RegisteredValue<T> value, Optional<Holder.Reference<T>> holder) {
    }
}
