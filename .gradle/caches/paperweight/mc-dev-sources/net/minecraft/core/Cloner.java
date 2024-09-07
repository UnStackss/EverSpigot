package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;

public class Cloner<T> {
    private final Codec<T> directCodec;

    Cloner(Codec<T> elementCodec) {
        this.directCodec = elementCodec;
    }

    public T clone(T value, HolderLookup.Provider subsetRegistry, HolderLookup.Provider fullRegistry) {
        DynamicOps<Object> dynamicOps = subsetRegistry.createSerializationContext(JavaOps.INSTANCE);
        DynamicOps<Object> dynamicOps2 = fullRegistry.createSerializationContext(JavaOps.INSTANCE);
        Object object = this.directCodec.encodeStart(dynamicOps, value).getOrThrow(error -> new IllegalStateException("Failed to encode: " + error));
        return this.directCodec.parse(dynamicOps2, object).getOrThrow(error -> new IllegalStateException("Failed to decode: " + error));
    }

    public static class Factory {
        private final Map<ResourceKey<? extends Registry<?>>, Cloner<?>> codecs = new HashMap<>();

        public <T> Cloner.Factory addCodec(ResourceKey<? extends Registry<? extends T>> registryRef, Codec<T> elementCodec) {
            this.codecs.put(registryRef, new Cloner<>(elementCodec));
            return this;
        }

        @Nullable
        public <T> Cloner<T> cloner(ResourceKey<? extends Registry<? extends T>> registryRef) {
            return (Cloner<T>)this.codecs.get(registryRef);
        }
    }
}
