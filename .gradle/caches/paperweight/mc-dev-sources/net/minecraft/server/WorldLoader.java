package net.minecraft.server;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.WorldDataConfiguration;
import org.slf4j.Logger;

public class WorldLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <D, R> CompletableFuture<R> load(
        WorldLoader.InitConfig serverConfig,
        WorldLoader.WorldDataSupplier<D> loadContextSupplier,
        WorldLoader.ResultFactory<D, R> saveApplierFactory,
        Executor prepareExecutor,
        Executor applyExecutor
    ) {
        try {
            Pair<WorldDataConfiguration, CloseableResourceManager> pair = serverConfig.packConfig.createResourceManager();
            CloseableResourceManager closeableResourceManager = pair.getSecond();
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = RegistryLayer.createRegistryAccess();
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess2 = loadAndReplaceLayer(
                closeableResourceManager, layeredRegistryAccess, RegistryLayer.WORLDGEN, RegistryDataLoader.WORLDGEN_REGISTRIES
            );
            RegistryAccess.Frozen frozen = layeredRegistryAccess2.getAccessForLoading(RegistryLayer.DIMENSIONS);
            RegistryAccess.Frozen frozen2 = RegistryDataLoader.load(closeableResourceManager, frozen, RegistryDataLoader.DIMENSION_REGISTRIES);
            WorldDataConfiguration worldDataConfiguration = pair.getFirst();
            WorldLoader.DataLoadOutput<D> dataLoadOutput = loadContextSupplier.get(
                new WorldLoader.DataLoadContext(closeableResourceManager, worldDataConfiguration, frozen, frozen2)
            );
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess3 = layeredRegistryAccess2.replaceFrom(
                RegistryLayer.DIMENSIONS, dataLoadOutput.finalDimensions
            );
            return ReloadableServerResources.loadResources(
                    closeableResourceManager,
                    layeredRegistryAccess3,
                    worldDataConfiguration.enabledFeatures(),
                    serverConfig.commandSelection(),
                    serverConfig.functionCompilationLevel(),
                    prepareExecutor,
                    applyExecutor
                )
                .whenComplete((dataPackContents, throwable) -> {
                    if (throwable != null) {
                        closeableResourceManager.close();
                    }
                })
                .thenApplyAsync(dataPackContents -> {
                    dataPackContents.updateRegistryTags();
                    return saveApplierFactory.create(closeableResourceManager, dataPackContents, layeredRegistryAccess3, dataLoadOutput.cookie);
                }, applyExecutor);
        } catch (Exception var14) {
            return CompletableFuture.failedFuture(var14);
        }
    }

    private static RegistryAccess.Frozen loadLayer(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> combinedDynamicRegistries,
        RegistryLayer type,
        List<RegistryDataLoader.RegistryData<?>> entries
    ) {
        RegistryAccess.Frozen frozen = combinedDynamicRegistries.getAccessForLoading(type);
        return RegistryDataLoader.load(resourceManager, frozen, entries);
    }

    public static LayeredRegistryAccess<RegistryLayer> loadAndReplaceLayer(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> combinedDynamicRegistries,
        RegistryLayer type,
        List<RegistryDataLoader.RegistryData<?>> entries
    ) {
        RegistryAccess.Frozen frozen = loadLayer(resourceManager, combinedDynamicRegistries, type, entries);
        return combinedDynamicRegistries.replaceFrom(type, frozen);
    }

    public static record DataLoadContext(
        ResourceManager resources, WorldDataConfiguration dataConfiguration, RegistryAccess.Frozen datapackWorldgen, RegistryAccess.Frozen datapackDimensions
    ) {
    }

    public static record DataLoadOutput<D>(D cookie, RegistryAccess.Frozen finalDimensions) {
    }

    public static record InitConfig(WorldLoader.PackConfig packConfig, Commands.CommandSelection commandSelection, int functionCompilationLevel) {
    }

    public static record PackConfig(PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean safeMode, boolean initMode) {
        public Pair<WorldDataConfiguration, CloseableResourceManager> createResourceManager() {
            WorldDataConfiguration worldDataConfiguration = MinecraftServer.configurePackRepository(
                this.packRepository, this.initialDataConfig, this.initMode, this.safeMode
            );
            List<PackResources> list = this.packRepository.openAllSelected();
            CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, list);
            return Pair.of(worldDataConfiguration, closeableResourceManager);
        }
    }

    @FunctionalInterface
    public interface ResultFactory<D, R> {
        R create(
            CloseableResourceManager resourceManager,
            ReloadableServerResources dataPackContents,
            LayeredRegistryAccess<RegistryLayer> combinedDynamicRegistries,
            D loadContext
        );
    }

    @FunctionalInterface
    public interface WorldDataSupplier<D> {
        WorldLoader.DataLoadOutput<D> get(WorldLoader.DataLoadContext context);
    }
}
