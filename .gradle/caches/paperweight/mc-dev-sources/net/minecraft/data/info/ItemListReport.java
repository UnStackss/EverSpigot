package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;

public class ItemListReport implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public ItemListReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        this.output = output;
        this.registries = registryLookupFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        Path path = this.output.getOutputFolder(PackOutput.Target.REPORTS).resolve("items.json");
        return this.registries
            .thenCompose(
                registryLookup -> {
                    JsonObject jsonObject = new JsonObject();
                    RegistryOps<JsonElement> registryOps = registryLookup.createSerializationContext(JsonOps.INSTANCE);
                    registryLookup.lookupOrThrow(Registries.ITEM)
                        .listElements()
                        .forEach(
                            entry -> {
                                JsonObject jsonObject2 = new JsonObject();
                                jsonObject2.add(
                                    "components",
                                    DataComponentMap.CODEC
                                        .encodeStart(registryOps, entry.value().components())
                                        .getOrThrow(components -> new IllegalStateException("Failed to encode components: " + components))
                                );
                                jsonObject.add(entry.getRegisteredName(), jsonObject2);
                            }
                        );
                    return DataProvider.saveStable(writer, jsonObject, path);
                }
            );
    }

    @Override
    public final String getName() {
        return "Item List";
    }
}
