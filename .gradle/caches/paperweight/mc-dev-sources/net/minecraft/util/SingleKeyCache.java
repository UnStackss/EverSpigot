package net.minecraft.util;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

public class SingleKeyCache<K, V> {
    private final Function<K, V> computeValue;
    @Nullable
    private K cacheKey = (K)null;
    @Nullable
    private V cachedValue;

    public SingleKeyCache(Function<K, V> mapper) {
        this.computeValue = mapper;
    }

    public V getValue(K input) {
        if (this.cachedValue == null || !Objects.equals(this.cacheKey, input)) {
            this.cachedValue = this.computeValue.apply(input);
            this.cacheKey = input;
        }

        return this.cachedValue;
    }
}
