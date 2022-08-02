package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.slf4j.Logger;

public class ReloadableServerResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CompletableFuture<Unit> DATA_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private final ReloadableServerRegistries.Holder fullRegistryHolder;
    public final ReloadableServerResources.ConfigurableRegistryLookup registryLookup;
    public Commands commands;
    private final RecipeManager recipes;
    private final TagManager tagManager;
    private final ServerAdvancementManager advancements;
    private final ServerFunctionLibrary functionLibrary;

    private ReloadableServerResources(
        RegistryAccess.Frozen dynamicRegistryManager, FeatureFlagSet enabledFeatures, Commands.CommandSelection environment, int functionPermissionLevel
    ) {
        this.fullRegistryHolder = new ReloadableServerRegistries.Holder(dynamicRegistryManager);
        this.registryLookup = new ReloadableServerResources.ConfigurableRegistryLookup(dynamicRegistryManager);
        this.registryLookup.missingTagAccessPolicy(ReloadableServerResources.MissingTagAccessPolicy.CREATE_NEW);
        this.recipes = new RecipeManager(this.registryLookup);
        this.tagManager = new TagManager(dynamicRegistryManager);
        this.commands = new Commands(environment, CommandBuildContext.simple(this.registryLookup, enabledFeatures));
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setDispatcher(this.commands, CommandBuildContext.simple(this.registryLookup, enabledFeatures)); // Paper - Brigadier Command API
        this.advancements = new ServerAdvancementManager(this.registryLookup);
        this.functionLibrary = new ServerFunctionLibrary(functionPermissionLevel, this.commands.getDispatcher());
    }

    public ServerFunctionLibrary getFunctionLibrary() {
        return this.functionLibrary;
    }

    public ReloadableServerRegistries.Holder fullRegistries() {
        return this.fullRegistryHolder;
    }

    public RecipeManager getRecipeManager() {
        return this.recipes;
    }

    public Commands getCommands() {
        return this.commands;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.advancements;
    }

    public List<PreparableReloadListener> listeners() {
        return List.of(this.tagManager, this.recipes, this.functionLibrary, this.advancements);
    }

    public static CompletableFuture<ReloadableServerResources> loadResources(
        ResourceManager manager,
        LayeredRegistryAccess<RegistryLayer> dynamicRegistries,
        FeatureFlagSet enabledFeatures,
        Commands.CommandSelection environment,
        int functionPermissionLevel,
        Executor prepareExecutor,
        Executor applyExecutor
    ) {
        return ReloadableServerRegistries.reload(dynamicRegistries, manager, prepareExecutor)
            .thenCompose(
                reloadedDynamicRegistries -> {
                    ReloadableServerResources reloadableServerResources = new ReloadableServerResources(
                        reloadedDynamicRegistries.compositeAccess(), enabledFeatures, environment, functionPermissionLevel
                    );
                    // Paper start - call commands event for bootstraps
                    //noinspection ConstantValue
                    io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(
                        io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE,
                        io.papermc.paper.plugin.bootstrap.BootstrapContext.class,
                        MinecraftServer.getServer() == null ? io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL : io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.RELOAD);
                    // Paper end - call commands event
                    return SimpleReloadInstance.create(
                            manager, reloadableServerResources.listeners(), prepareExecutor, applyExecutor, DATA_RELOAD_INITIAL_TASK, LOGGER.isDebugEnabled()
                        )
                        .done()
                        .whenComplete(
                            (void_, throwable) -> reloadableServerResources.registryLookup
                                    .missingTagAccessPolicy(ReloadableServerResources.MissingTagAccessPolicy.FAIL)
                        )
                        .thenApply(void_ -> reloadableServerResources);
                }
            );
    }

    public void updateRegistryTags() {
        this.tagManager.getResult().forEach(tags -> updateRegistryTags(this.fullRegistryHolder.get(), (TagManager.LoadResult<?>)tags));
        AbstractFurnaceBlockEntity.invalidateCache();
        Blocks.rebuildCache();
    }

    private static <T> void updateRegistryTags(RegistryAccess dynamicRegistryManager, TagManager.LoadResult<T> tags) {
        ResourceKey<? extends Registry<T>> resourceKey = tags.key();
        Map<TagKey<T>, List<Holder<T>>> map = tags.tags()
            .entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(entry -> TagKey.create(resourceKey, entry.getKey()), entry -> List.copyOf(entry.getValue())));
        dynamicRegistryManager.registryOrThrow(resourceKey).bindTags(map);
    }

    static class ConfigurableRegistryLookup implements HolderLookup.Provider {
        private final RegistryAccess registryAccess;
        ReloadableServerResources.MissingTagAccessPolicy missingTagAccessPolicy = ReloadableServerResources.MissingTagAccessPolicy.FAIL;

        ConfigurableRegistryLookup(RegistryAccess dynamicRegistryManager) {
            this.registryAccess = dynamicRegistryManager;
        }

        public void missingTagAccessPolicy(ReloadableServerResources.MissingTagAccessPolicy entryListCreationPolicy) {
            this.missingTagAccessPolicy = entryListCreationPolicy;
        }

        @Override
        public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
            return this.registryAccess.listRegistries();
        }

        @Override
        public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
            return this.registryAccess.registry(registryRef).map(registry -> this.createDispatchedLookup(registry.asLookup(), registry.asTagAddingLookup()));
        }

        private <T> HolderLookup.RegistryLookup<T> createDispatchedLookup(
            HolderLookup.RegistryLookup<T> readOnlyWrapper, HolderLookup.RegistryLookup<T> tagCreatingWrapper
        ) {
            return new HolderLookup.RegistryLookup.Delegate<T>() {
                @Override
                public HolderLookup.RegistryLookup<T> parent() {
                    return switch (ConfigurableRegistryLookup.this.missingTagAccessPolicy) {
                        case CREATE_NEW -> tagCreatingWrapper;
                        case FAIL -> readOnlyWrapper;
                    };
                }
            };
        }
    }

    static enum MissingTagAccessPolicy {
        CREATE_NEW,
        FAIL;
    }
}
