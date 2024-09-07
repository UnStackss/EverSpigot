package net.minecraft.server.packs.resources;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.Unit;
import org.slf4j.Logger;

public class ReloadableResourceManager implements ResourceManager, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private CloseableResourceManager resources;
    private final List<PreparableReloadListener> listeners = Lists.newArrayList();
    private final PackType type;

    public ReloadableResourceManager(PackType type) {
        this.type = type;
        this.resources = new MultiPackResourceManager(type, List.of());
    }

    @Override
    public void close() {
        this.resources.close();
    }

    public void registerReloadListener(PreparableReloadListener reloader) {
        this.listeners.add(reloader);
    }

    public ReloadInstance createReload(Executor prepareExecutor, Executor applyExecutor, CompletableFuture<Unit> initialStage, List<PackResources> packs) {
        LOGGER.info("Reloading ResourceManager: {}", LogUtils.defer(() -> packs.stream().map(PackResources::packId).collect(Collectors.joining(", "))));
        this.resources.close();
        this.resources = new MultiPackResourceManager(this.type, packs);
        return SimpleReloadInstance.create(this.resources, this.listeners, prepareExecutor, applyExecutor, initialStage, LOGGER.isDebugEnabled());
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation id) {
        return this.resources.getResource(id);
    }

    @Override
    public Set<String> getNamespaces() {
        return this.resources.getNamespaces();
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation id) {
        return this.resources.getResourceStack(id);
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String startingPath, Predicate<ResourceLocation> allowedPathPredicate) {
        return this.resources.listResources(startingPath, allowedPathPredicate);
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String startingPath, Predicate<ResourceLocation> allowedPathPredicate) {
        return this.resources.listResourceStacks(startingPath, allowedPathPredicate);
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.resources.listPacks();
    }
}
