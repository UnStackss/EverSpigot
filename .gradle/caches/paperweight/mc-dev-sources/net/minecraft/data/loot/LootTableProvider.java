package net.minecraft.data.loot;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

public class LootTableProvider implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput.PathProvider pathProvider;
    private final Set<ResourceKey<LootTable>> requiredTables;
    private final List<LootTableProvider.SubProviderEntry> subProviders;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public LootTableProvider(
        PackOutput output,
        Set<ResourceKey<LootTable>> lootTableIds,
        List<LootTableProvider.SubProviderEntry> lootTypeGenerators,
        CompletableFuture<HolderLookup.Provider> registryLookupFuture
    ) {
        this.pathProvider = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
        this.subProviders = lootTypeGenerators;
        this.requiredTables = lootTableIds;
        this.registries = registryLookupFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        return this.registries.thenCompose(registryLookup -> this.run(writer, registryLookup));
    }

    private CompletableFuture<?> run(CachedOutput writer, HolderLookup.Provider registryLookup) {
        WritableRegistry<LootTable> writableRegistry = new MappedRegistry<>(Registries.LOOT_TABLE, Lifecycle.experimental());
        Map<RandomSupport.Seed128bit, ResourceLocation> map = new Object2ObjectOpenHashMap<>();
        this.subProviders.forEach(lootTypeGenerator -> lootTypeGenerator.provider().apply(registryLookup).generate((lootTable, builder) -> {
                ResourceLocation resourceLocation = sequenceIdForLootTable(lootTable);
                ResourceLocation resourceLocation2 = map.put(RandomSequence.seedForKey(resourceLocation), resourceLocation);
                if (resourceLocation2 != null) {
                    Util.logAndPauseIfInIde("Loot table random sequence seed collision on " + resourceLocation2 + " and " + lootTable.location());
                }

                builder.setRandomSequence(resourceLocation);
                LootTable lootTable2 = builder.setParamSet(lootTypeGenerator.paramSet).build();
                writableRegistry.register(lootTable, lootTable2, RegistrationInfo.BUILT_IN);
            }));
        writableRegistry.freeze();
        ProblemReporter.Collector collector = new ProblemReporter.Collector();
        HolderGetter.Provider provider = new RegistryAccess.ImmutableRegistryAccess(List.of(writableRegistry)).freeze().asGetterLookup();
        ValidationContext validationContext = new ValidationContext(collector, LootContextParamSets.ALL_PARAMS, provider);

        for (ResourceKey<LootTable> resourceKey : Sets.difference(this.requiredTables, writableRegistry.registryKeySet())) {
            collector.report("Missing built-in table: " + resourceKey.location());
        }

        writableRegistry.holders()
            .forEach(
                entry -> entry.value()
                        .validate(validationContext.setParams(entry.value().getParamSet()).enterElement("{" + entry.key().location() + "}", entry.key()))
            );
        Multimap<String, String> multimap = collector.get();
        if (!multimap.isEmpty()) {
            multimap.forEach((name, message) -> LOGGER.warn("Found validation problem in {}: {}", name, message));
            throw new IllegalStateException("Failed to validate loot tables, see logs");
        } else {
            return CompletableFuture.allOf(writableRegistry.entrySet().stream().map(entry -> {
                ResourceKey<LootTable> resourceKeyx = entry.getKey();
                LootTable lootTable = entry.getValue();
                Path path = this.pathProvider.json(resourceKeyx.location());
                return DataProvider.saveStable(writer, registryLookup, LootTable.DIRECT_CODEC, lootTable, path);
            }).toArray(CompletableFuture[]::new));
        }
    }

    private static ResourceLocation sequenceIdForLootTable(ResourceKey<LootTable> lootTableKey) {
        return lootTableKey.location();
    }

    @Override
    public final String getName() {
        return "Loot Tables";
    }

    public static record SubProviderEntry(Function<HolderLookup.Provider, LootTableSubProvider> provider, LootContextParamSet paramSet) {
    }
}
