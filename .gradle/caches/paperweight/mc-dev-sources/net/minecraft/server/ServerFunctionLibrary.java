package net.minecraft.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ServerFunctionLibrary implements PreparableReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Registry<CommandFunction<CommandSourceStack>>> TYPE_KEY = ResourceKey.createRegistryKey(
        ResourceLocation.withDefaultNamespace("function")
    );
    private static final FileToIdConverter LISTER = new FileToIdConverter(Registries.elementsDirPath(TYPE_KEY), ".mcfunction");
    private volatile Map<ResourceLocation, CommandFunction<CommandSourceStack>> functions = ImmutableMap.of();
    private final TagLoader<CommandFunction<CommandSourceStack>> tagsLoader = new TagLoader<>(this::getFunction, Registries.tagsDirPath(TYPE_KEY));
    private volatile Map<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> tags = Map.of();
    private final int functionCompilationLevel;
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public Optional<CommandFunction<CommandSourceStack>> getFunction(ResourceLocation id) {
        return Optional.ofNullable(this.functions.get(id));
    }

    public Map<ResourceLocation, CommandFunction<CommandSourceStack>> getFunctions() {
        return this.functions;
    }

    public Collection<CommandFunction<CommandSourceStack>> getTag(ResourceLocation id) {
        return this.tags.getOrDefault(id, List.of());
    }

    public Iterable<ResourceLocation> getAvailableTags() {
        return this.tags.keySet();
    }

    public ServerFunctionLibrary(int level, CommandDispatcher<CommandSourceStack> commandDispatcher) {
        this.functionCompilationLevel = level;
        this.dispatcher = commandDispatcher;
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
        CompletableFuture<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> completableFuture = CompletableFuture.supplyAsync(
            () -> this.tagsLoader.load(manager), prepareExecutor
        );
        CompletableFuture<Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>>> completableFuture2 = CompletableFuture.<Map<ResourceLocation, Resource>>supplyAsync(
                () -> LISTER.listMatchingResources(manager), prepareExecutor
            )
            .thenCompose(
                functions -> {
                    Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>> map = Maps.newHashMap();
                    CommandSourceStack commandSourceStack = new CommandSourceStack(
                        CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, this.functionCompilationLevel, "", CommonComponents.EMPTY, null, null
                    );

                    for (Entry<ResourceLocation, Resource> entry : functions.entrySet()) {
                        ResourceLocation resourceLocation = entry.getKey();
                        ResourceLocation resourceLocation2 = LISTER.fileToId(resourceLocation);
                        map.put(resourceLocation2, CompletableFuture.supplyAsync(() -> {
                            List<String> list = readLines(entry.getValue());
                            return CommandFunction.fromLines(resourceLocation2, this.dispatcher, commandSourceStack, list);
                        }, prepareExecutor));
                    }

                    CompletableFuture<?>[] completableFutures = map.values().toArray(new CompletableFuture[0]);
                    return CompletableFuture.allOf(completableFutures).handle((unused, ex) -> map);
                }
            );
        return completableFuture.thenCombine(completableFuture2, Pair::of)
            .thenCompose(synchronizer::wait)
            .thenAcceptAsync(
                intermediate -> {
                    Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>> map = (Map<ResourceLocation, CompletableFuture<CommandFunction<CommandSourceStack>>>)intermediate.getSecond();
                    Builder<ResourceLocation, CommandFunction<CommandSourceStack>> builder = ImmutableMap.builder();
                    map.forEach((id, functionFuture) -> functionFuture.handle((function, ex) -> {
                            if (ex != null) {
                                LOGGER.error("Failed to load function {}", id, ex);
                            } else {
                                builder.put(id, function);
                            }

                            return null;
                        }).join());
                    this.functions = builder.build();
                    this.tags = this.tagsLoader.build((Map<ResourceLocation, List<TagLoader.EntryWithSource>>)intermediate.getFirst());
                },
                applyExecutor
            );
    }

    private static List<String> readLines(Resource resource) {
        try {
            List var2;
            try (BufferedReader bufferedReader = resource.openAsReader()) {
                var2 = bufferedReader.lines().toList();
            }

            return var2;
        } catch (IOException var6) {
            throw new CompletionException(var6);
        }
    }
}
