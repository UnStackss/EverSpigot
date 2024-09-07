package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

public class ResourceKey<T> {
    private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = new MapMaker().weakValues().makeMap();
    private final ResourceLocation registryName;
    private final ResourceLocation location;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return ResourceLocation.CODEC.xmap(id -> create(registry, id), ResourceKey::location);
    }

    public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registry) {
        return ResourceLocation.STREAM_CODEC.map(id -> create(registry, id), ResourceKey::location);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registry, ResourceLocation value) {
        return create(registry.location, value);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation registry) {
        return create(Registries.ROOT_REGISTRY_NAME, registry);
    }

    private static <T> ResourceKey<T> create(ResourceLocation registry, ResourceLocation value) {
        return (ResourceKey<T>)VALUES.computeIfAbsent(new ResourceKey.InternKey(registry, value), pair -> new ResourceKey(pair.registry, pair.location));
    }

    private ResourceKey(ResourceLocation registry, ResourceLocation value) {
        this.registryName = registry;
        this.location = value;
    }

    @Override
    public String toString() {
        return "ResourceKey[" + this.registryName + " / " + this.location + "]";
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registry) {
        return this.registryName.equals(registry.location());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registryRef) {
        return this.isFor(registryRef) ? Optional.of((ResourceKey<E>)this) : Optional.empty();
    }

    public ResourceLocation location() {
        return this.location;
    }

    public ResourceLocation registry() {
        return this.registryName;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return createRegistryKey(this.registryName);
    }

    static record InternKey(ResourceLocation registry, ResourceLocation location) {
    }
}
