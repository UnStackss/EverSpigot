package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {
    private final ResourceLocation defaultKey;
    private Holder.Reference<T> defaultValue;

    public DefaultedMappedRegistry(String defaultId, ResourceKey<? extends Registry<T>> key, Lifecycle lifecycle, boolean intrusive) {
        super(key, lifecycle, intrusive);
        this.defaultKey = ResourceLocation.parse(defaultId);
    }

    @Override
    public Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo info) {
        Holder.Reference<T> reference = super.register(key, value, info);
        if (this.defaultKey.equals(key.location())) {
            this.defaultValue = reference;
        }

        return reference;
    }

    @Override
    public int getId(@Nullable T value) {
        int i = super.getId(value);
        return i == -1 ? super.getId(this.defaultValue.value()) : i;
    }

    @Nonnull
    @Override
    public ResourceLocation getKey(T value) {
        ResourceLocation resourceLocation = super.getKey(value);
        return resourceLocation == null ? this.defaultKey : resourceLocation;
    }

    @Nonnull
    @Override
    public T get(@Nullable ResourceLocation id) {
        T object = super.get(id);
        return object == null ? this.defaultValue.value() : object;
    }

    @Override
    public Optional<T> getOptional(@Nullable ResourceLocation id) {
        return Optional.ofNullable(super.get(id));
    }

    @Override
    public Optional<Holder.Reference<T>> getAny() {
        return Optional.ofNullable(this.defaultValue);
    }

    @Nonnull
    @Override
    public T byId(int index) {
        T object = super.byId(index);
        return object == null ? this.defaultValue.value() : object;
    }

    @Override
    public Optional<Holder.Reference<T>> getRandom(RandomSource random) {
        return super.getRandom(random).or(() -> Optional.of(this.defaultValue));
    }

    @Override
    public ResourceLocation getDefaultKey() {
        return this.defaultKey;
    }
}
