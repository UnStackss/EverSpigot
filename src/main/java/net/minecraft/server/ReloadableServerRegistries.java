package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class ReloadableServerRegistries {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final RegistrationInfo DEFAULT_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());

    public static CompletableFuture<LayeredRegistryAccess<RegistryLayer>> reload(
        LayeredRegistryAccess<RegistryLayer> dynamicRegistries, ResourceManager resourceManager, Executor prepareExecutor
    ) {
        RegistryAccess.Frozen frozen = dynamicRegistries.getAccessForLoading(RegistryLayer.RELOADABLE);
        RegistryOps<JsonElement> registryOps = new ReloadableServerRegistries.EmptyTagLookupWrapper(frozen).createSerializationContext(JsonOps.INSTANCE);
        final io.papermc.paper.registry.data.util.Conversions conversions = new io.papermc.paper.registry.data.util.Conversions(registryOps.lookupProvider); // Paper
        List<CompletableFuture<WritableRegistry<?>>> list = LootDataType.values()
            .map(type -> scheduleElementParse((LootDataType<?>)type, registryOps, resourceManager, prepareExecutor, conversions)) // Paper
            .toList();
        CompletableFuture<List<WritableRegistry<?>>> completableFuture = Util.sequence(list);
        return completableFuture.thenApplyAsync(registries -> apply(dynamicRegistries, (List<WritableRegistry<?>>)registries), prepareExecutor);
    }

    private static <T> CompletableFuture<WritableRegistry<?>> scheduleElementParse(
        LootDataType<T> type, RegistryOps<JsonElement> ops, ResourceManager resourceManager, Executor prepareExecutor, io.papermc.paper.registry.data.util.Conversions conversions // Paper
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                WritableRegistry<T> writableRegistry = new MappedRegistry<>(type.registryKey(), Lifecycle.experimental());
                io.papermc.paper.registry.PaperRegistryAccess.instance().registerReloadableRegistry(type.registryKey(), writableRegistry); // Paper - register reloadable registry
                Map<ResourceLocation, JsonElement> map = new HashMap<>();
                String string = Registries.elementsDirPath(type.registryKey());
                SimpleJsonResourceReloadListener.scanDirectory(resourceManager, string, GSON, map);
                map.forEach(
                    (id, json) -> type.deserialize(id, ops, json)
                            .ifPresent(value -> io.papermc.paper.registry.PaperRegistryListenerManager.INSTANCE.registerWithListeners(writableRegistry, ResourceKey.create(type.registryKey(), id), value, DEFAULT_REGISTRATION_INFO, conversions)) // Paper - register with listeners
                );
                return writableRegistry;
            },
            prepareExecutor
        );
    }

    private static LayeredRegistryAccess<RegistryLayer> apply(LayeredRegistryAccess<RegistryLayer> dynamicRegistries, List<WritableRegistry<?>> registries) {
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = createUpdatedRegistries(dynamicRegistries, registries);
        ProblemReporter.Collector collector = new ProblemReporter.Collector();
        RegistryAccess.Frozen frozen = layeredRegistryAccess.compositeAccess();
        ValidationContext validationContext = new ValidationContext(collector, LootContextParamSets.ALL_PARAMS, frozen.asGetterLookup());
        LootDataType.values().forEach(lootDataType -> validateRegistry(validationContext, (LootDataType<?>)lootDataType, frozen));
        collector.get().forEach((path, message) -> LOGGER.warn("Found loot table element validation problem in {}: {}", path, message));
        return layeredRegistryAccess;
    }

    private static LayeredRegistryAccess<RegistryLayer> createUpdatedRegistries(
        LayeredRegistryAccess<RegistryLayer> dynamicRegistries, List<WritableRegistry<?>> registries
    ) {
        RegistryAccess registryAccess = new RegistryAccess.ImmutableRegistryAccess(registries);
        ((WritableRegistry)registryAccess.<LootTable>registryOrThrow(Registries.LOOT_TABLE))
            .register(BuiltInLootTables.EMPTY, LootTable.EMPTY, DEFAULT_REGISTRATION_INFO);
        return dynamicRegistries.replaceFrom(RegistryLayer.RELOADABLE, registryAccess.freeze());
    }

    private static <T> void validateRegistry(ValidationContext reporter, LootDataType<T> lootDataType, RegistryAccess registryManager) {
        Registry<T> registry = registryManager.registryOrThrow(lootDataType.registryKey());
        registry.holders().forEach(entry -> lootDataType.runValidation(reporter, entry.key(), entry.value()));
    }

    static class EmptyTagLookupWrapper implements HolderLookup.Provider {
        private final RegistryAccess registryAccess;

        EmptyTagLookupWrapper(RegistryAccess registryManager) {
            this.registryAccess = registryManager;
        }

        @Override
        public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
            return this.registryAccess.listRegistries();
        }

        @Override
        public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
            return this.registryAccess.registry(registryRef).map(Registry::asTagAddingLookup);
        }
    }

    public static class Holder {
        private final RegistryAccess.Frozen registries;

        public Holder(RegistryAccess.Frozen registryManager) {
            this.registries = registryManager;
        }

        public RegistryAccess.Frozen get() {
            return this.registries;
        }

        public HolderGetter.Provider lookup() {
            return this.registries.asGetterLookup();
        }

        public Collection<ResourceLocation> getKeys(ResourceKey<? extends Registry<?>> registryRef) {
            return this.registries.registry(registryRef).stream().flatMap(registry -> registry.holders().map(entry -> entry.key().location())).toList();
        }

        public LootTable getLootTable(ResourceKey<LootTable> key) {
            return this.registries
                .lookup(Registries.LOOT_TABLE)
                .flatMap(registryEntryLookup -> registryEntryLookup.get(key))
                .map(net.minecraft.core.Holder::value)
                .orElse(LootTable.EMPTY);
        }
    }
}
