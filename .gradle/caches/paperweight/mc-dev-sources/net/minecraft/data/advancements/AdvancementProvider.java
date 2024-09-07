package net.minecraft.data.advancements;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

public class AdvancementProvider implements DataProvider {
    private final PackOutput.PathProvider pathProvider;
    private final List<AdvancementSubProvider> subProviders;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public AdvancementProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture, List<AdvancementSubProvider> tabGenerators) {
        this.pathProvider = output.createRegistryElementsPathProvider(Registries.ADVANCEMENT);
        this.subProviders = tabGenerators;
        this.registries = registryLookupFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        return this.registries.thenCompose(lookup -> {
            Set<ResourceLocation> set = new HashSet<>();
            List<CompletableFuture<?>> list = new ArrayList<>();
            Consumer<AdvancementHolder> consumer = advancement -> {
                if (!set.add(advancement.id())) {
                    throw new IllegalStateException("Duplicate advancement " + advancement.id());
                } else {
                    Path path = this.pathProvider.json(advancement.id());
                    list.add(DataProvider.saveStable(writer, lookup, Advancement.CODEC, advancement.value(), path));
                }
            };

            for (AdvancementSubProvider advancementSubProvider : this.subProviders) {
                advancementSubProvider.generate(lookup, consumer);
            }

            return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
        });
    }

    @Override
    public final String getName() {
        return "Advancements";
    }
}
