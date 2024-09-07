package net.minecraft.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public interface DefaultedRegistry<T> extends Registry<T> {
    @Nonnull
    @Override
    ResourceLocation getKey(T value);

    @Nonnull
    @Override
    T get(@Nullable ResourceLocation id);

    @Nonnull
    @Override
    T byId(int index);

    ResourceLocation getDefaultKey();
}
