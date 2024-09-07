package net.minecraft.data.models;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.models.blockstates.BlockStateGenerator;
import net.minecraft.data.models.model.DelegatedModel;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModelProvider implements DataProvider {
    private final PackOutput.PathProvider blockStatePathProvider;
    private final PackOutput.PathProvider modelPathProvider;

    public ModelProvider(PackOutput output) {
        this.blockStatePathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.modelPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        Map<Block, BlockStateGenerator> map = Maps.newHashMap();
        Consumer<BlockStateGenerator> consumer = blockStateSupplier -> {
            Block block = blockStateSupplier.getBlock();
            BlockStateGenerator blockStateGenerator = map.put(block, blockStateSupplier);
            if (blockStateGenerator != null) {
                throw new IllegalStateException("Duplicate blockstate definition for " + block);
            }
        };
        Map<ResourceLocation, Supplier<JsonElement>> map2 = Maps.newHashMap();
        Set<Item> set = Sets.newHashSet();
        BiConsumer<ResourceLocation, Supplier<JsonElement>> biConsumer = (id, jsonSupplier) -> {
            Supplier<JsonElement> supplier = map2.put(id, jsonSupplier);
            if (supplier != null) {
                throw new IllegalStateException("Duplicate model definition for " + id);
            }
        };
        Consumer<Item> consumer2 = set::add;
        new BlockModelGenerators(consumer, biConsumer, consumer2).run();
        new ItemModelGenerators(biConsumer).run();
        List<Block> list = BuiltInRegistries.BLOCK
            .entrySet()
            .stream()
            .filter(entry -> true)
            .map(Entry::getValue)
            .filter(block -> !map.containsKey(block))
            .toList();
        if (!list.isEmpty()) {
            throw new IllegalStateException("Missing blockstate definitions for: " + list);
        } else {
            BuiltInRegistries.BLOCK.forEach(block -> {
                Item item = Item.BY_BLOCK.get(block);
                if (item != null) {
                    if (set.contains(item)) {
                        return;
                    }

                    ResourceLocation resourceLocation = ModelLocationUtils.getModelLocation(item);
                    if (!map2.containsKey(resourceLocation)) {
                        map2.put(resourceLocation, new DelegatedModel(ModelLocationUtils.getModelLocation(block)));
                    }
                }
            });
            return CompletableFuture.allOf(
                this.saveCollection(writer, map, block -> this.blockStatePathProvider.json(block.builtInRegistryHolder().key().location())),
                this.saveCollection(writer, map2, this.modelPathProvider::json)
            );
        }
    }

    private <T> CompletableFuture<?> saveCollection(CachedOutput cache, Map<T, ? extends Supplier<JsonElement>> models, Function<T, Path> pathGetter) {
        return CompletableFuture.allOf(models.entrySet().stream().map(entry -> {
            Path path = pathGetter.apply(entry.getKey());
            JsonElement jsonElement = entry.getValue().get();
            return DataProvider.saveStable(cache, jsonElement, path);
        }).toArray(CompletableFuture[]::new));
    }

    @Override
    public final String getName() {
        return "Model Definitions";
    }
}
