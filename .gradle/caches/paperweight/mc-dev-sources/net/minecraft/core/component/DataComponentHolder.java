package net.minecraft.core.component;

import javax.annotation.Nullable;

public interface DataComponentHolder {
    DataComponentMap getComponents();

    @Nullable
    default <T> T get(DataComponentType<? extends T> type) {
        return this.getComponents().get(type);
    }

    default <T> T getOrDefault(DataComponentType<? extends T> type, T fallback) {
        return this.getComponents().getOrDefault(type, fallback);
    }

    default boolean has(DataComponentType<?> type) {
        return this.getComponents().has(type);
    }
}
