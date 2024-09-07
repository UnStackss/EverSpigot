package net.minecraft.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.Tag;

public class EncoderCache {
    final LoadingCache<EncoderCache.Key<?, ?>, DataResult<?>> cache;

    public EncoderCache(int size) {
        this.cache = CacheBuilder.newBuilder()
            .maximumSize((long)size)
            .concurrencyLevel(1)
            .softValues()
            .build(new CacheLoader<EncoderCache.Key<?, ?>, DataResult<?>>() {
                @Override
                public DataResult<?> load(EncoderCache.Key<?, ?> key) {
                    return key.resolve();
                }
            });
    }

    public <A> Codec<A> wrap(Codec<A> codec) {
        return new Codec<A>() {
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> dynamicOps, T object) {
                return codec.decode(dynamicOps, object);
            }

            public <T> DataResult<T> encode(A object, DynamicOps<T> dynamicOps, T object2) {
                return EncoderCache.this.cache
                    .getUnchecked(new EncoderCache.Key<>(codec, object, dynamicOps))
                    .map(objectx -> objectx instanceof Tag tag ? tag.copy() : objectx);
            }
        };
    }

    static record Key<A, T>(Codec<A> codec, A value, DynamicOps<T> ops) {
        public DataResult<T> resolve() {
            return this.codec.encodeStart(this.ops, this.value);
        }

        @Override
        public boolean equals(Object object) {
            return this == object
                || object instanceof EncoderCache.Key<?, ?> key && this.codec == key.codec && this.value.equals(key.value) && this.ops.equals(key.ops);
        }

        @Override
        public int hashCode() {
            int i = System.identityHashCode(this.codec);
            i = 31 * i + this.value.hashCode();
            return 31 * i + this.ops.hashCode();
        }
    }
}
