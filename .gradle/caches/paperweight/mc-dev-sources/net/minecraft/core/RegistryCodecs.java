package net.minecraft.core;

import com.mojang.serialization.Codec;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;

public class RegistryCodecs {
    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryRef, Codec<E> elementCodec) {
        return homogeneousList(registryRef, elementCodec, false);
    }

    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryRef, Codec<E> elementCodec, boolean alwaysSerializeAsList) {
        return HolderSetCodec.create(registryRef, RegistryFileCodec.create(registryRef, elementCodec), alwaysSerializeAsList);
    }

    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryRef) {
        return homogeneousList(registryRef, false);
    }

    public static <E> Codec<HolderSet<E>> homogeneousList(ResourceKey<? extends Registry<E>> registryRef, boolean alwaysSerializeAsList) {
        return HolderSetCodec.create(registryRef, RegistryFixedCodec.create(registryRef), alwaysSerializeAsList);
    }
}
