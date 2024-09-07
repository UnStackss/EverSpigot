package net.minecraft.util.parsing.packrat;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Objects;
import javax.annotation.Nullable;

public final class Scope {
    private final Object2ObjectMap<Atom<?>, Object> values = new Object2ObjectArrayMap<>();

    public <T> void put(Atom<T> symbol, @Nullable T value) {
        this.values.put(symbol, value);
    }

    @Nullable
    public <T> T get(Atom<T> symbol) {
        return (T)this.values.get(symbol);
    }

    public <T> T getOrThrow(Atom<T> symbol) {
        return Objects.requireNonNull(this.get(symbol));
    }

    public <T> T getOrDefault(Atom<T> symbol, T fallback) {
        return Objects.requireNonNullElse(this.get(symbol), fallback);
    }

    @Nullable
    @SafeVarargs
    public final <T> T getAny(Atom<T>... symbols) {
        for (Atom<T> atom : symbols) {
            T object = this.get(atom);
            if (object != null) {
                return object;
            }
        }

        return null;
    }

    @SafeVarargs
    public final <T> T getAnyOrThrow(Atom<T>... symbols) {
        return Objects.requireNonNull(this.getAny(symbols));
    }

    @Override
    public String toString() {
        return this.values.toString();
    }

    public void putAll(Scope results) {
        this.values.putAll(results.values);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof Scope scope && this.values.equals(scope.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }
}
