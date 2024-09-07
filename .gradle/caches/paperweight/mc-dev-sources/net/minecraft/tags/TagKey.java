package net.minecraft.tags;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, ResourceLocation location) {
    private static final Interner<TagKey<?>> VALUES = Interners.newWeakInterner();

    @Deprecated
    public TagKey(ResourceKey<? extends Registry<T>> registry, ResourceLocation location) {
        this.registry = registry;
        this.location = location;
    }

    public static <T> Codec<TagKey<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return ResourceLocation.CODEC.xmap(id -> create(registry, id), TagKey::location);
    }

    public static <T> Codec<TagKey<T>> hashedCodec(ResourceKey<? extends Registry<T>> registry) {
        return Codec.STRING
            .comapFlatMap(
                string -> string.startsWith("#")
                        ? ResourceLocation.read(string.substring(1)).map(id -> create(registry, id))
                        : DataResult.error(() -> "Not a tag id"),
                string -> "#" + string.location
            );
    }

    public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        return (TagKey<T>)VALUES.intern(new TagKey<>(registry, id));
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> registryRef) {
        return this.registry == registryRef;
    }

    public <E> Optional<TagKey<E>> cast(ResourceKey<? extends Registry<E>> registryRef) {
        return this.isFor(registryRef) ? Optional.of((TagKey<E>)this) : Optional.empty();
    }

    @Override
    public String toString() {
        return "TagKey[" + this.registry.location() + " / " + this.location + "]";
    }
}
