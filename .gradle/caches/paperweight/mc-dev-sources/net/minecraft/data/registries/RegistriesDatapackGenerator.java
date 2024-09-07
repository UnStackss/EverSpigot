package net.minecraft.data.registries;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;

public class RegistriesDatapackGenerator implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public RegistriesDatapackGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        this.registries = registryLookupFuture;
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        return this.registries
            .thenCompose(
                lookup -> {
                    DynamicOps<JsonElement> dynamicOps = lookup.createSerializationContext(JsonOps.INSTANCE);
                    return CompletableFuture.allOf(
                        RegistryDataLoader.WORLDGEN_REGISTRIES
                            .stream()
                            .flatMap(entry -> this.dumpRegistryCap(writer, lookup, dynamicOps, (RegistryDataLoader.RegistryData<?>)entry).stream())
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private <T> Optional<CompletableFuture<?>> dumpRegistryCap(
        CachedOutput writer, HolderLookup.Provider lookup, DynamicOps<JsonElement> ops, RegistryDataLoader.RegistryData<T> registry
    ) {
        ResourceKey<? extends Registry<T>> resourceKey = registry.key();
        return lookup.lookup(resourceKey)
            .map(
                wrapper -> {
                    PackOutput.PathProvider pathProvider = this.output.createRegistryElementsPathProvider(resourceKey);
                    return CompletableFuture.allOf(
                        wrapper.listElements()
                            .map(entry -> dumpValue(pathProvider.json(entry.key().location()), writer, ops, registry.elementCodec(), entry.value()))
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    private static <E> CompletableFuture<?> dumpValue(Path path, CachedOutput cache, DynamicOps<JsonElement> json, Encoder<E> encoder, E value) {
        return encoder.encodeStart(json, value)
            .mapOrElse(
                jsonElement -> DataProvider.saveStable(cache, jsonElement, path),
                error -> CompletableFuture.failedFuture(new IllegalStateException("Couldn't generate file '" + path + "': " + error.message()))
            );
    }

    @Override
    public final String getName() {
        return "Registries";
    }
}
