package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier synchronizer,
        ResourceManager manager,
        ProfilerFiller prepareProfiler,
        ProfilerFiller applyProfiler,
        Executor prepareExecutor,
        Executor applyExecutor
    ) {
        return CompletableFuture.<T>supplyAsync(() -> this.prepare(manager, prepareProfiler), prepareExecutor)
            .thenCompose(synchronizer::wait)
            .thenAcceptAsync(prepared -> this.apply((T)prepared, manager, applyProfiler), applyExecutor);
    }

    protected abstract T prepare(ResourceManager manager, ProfilerFiller profiler);

    protected abstract void apply(T prepared, ResourceManager manager, ProfilerFiller profiler);
}
