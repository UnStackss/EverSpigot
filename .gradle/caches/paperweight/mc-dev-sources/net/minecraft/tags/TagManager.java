package net.minecraft.tags;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class TagManager implements PreparableReloadListener {
    private final RegistryAccess registryAccess;
    private List<TagManager.LoadResult<?>> results = List.of();

    public TagManager(RegistryAccess registryManager) {
        this.registryAccess = registryManager;
    }

    public List<TagManager.LoadResult<?>> getResult() {
        return this.results;
    }

    @Override
    public CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier synchronizer,
        ResourceManager manager,
        ProfilerFiller prepareProfiler,
        ProfilerFiller applyProfiler,
        Executor prepareExecutor,
        Executor applyExecutor
    ) {
        List<? extends CompletableFuture<? extends TagManager.LoadResult<?>>> list = this.registryAccess
            .registries()
            .map(registry -> this.createLoader(manager, prepareExecutor, (RegistryAccess.RegistryEntry<?>)registry))
            .toList();
        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new))
            .thenCompose(synchronizer::wait)
            .thenAcceptAsync(void_ -> this.results = list.stream().map(CompletableFuture::join).collect(Collectors.toUnmodifiableList()), applyExecutor);
    }

    private <T> CompletableFuture<TagManager.LoadResult<T>> createLoader(
        ResourceManager resourceManager, Executor prepareExecutor, RegistryAccess.RegistryEntry<T> requirement
    ) {
        ResourceKey<? extends Registry<T>> resourceKey = requirement.key();
        Registry<T> registry = requirement.value();
        TagLoader<Holder<T>> tagLoader = new TagLoader<>(registry::getHolder, Registries.tagsDirPath(resourceKey));
        return CompletableFuture.supplyAsync(() -> new TagManager.LoadResult<>(resourceKey, tagLoader.loadAndBuild(resourceManager)), prepareExecutor);
    }

    public static record LoadResult<T>(ResourceKey<? extends Registry<T>> key, Map<ResourceLocation, Collection<Holder<T>>> tags) {
    }
}
