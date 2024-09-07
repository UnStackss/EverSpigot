package net.minecraft.data.info;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.slf4j.Logger;

public class BiomeParametersDumpReport implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path topPath;
    private final CompletableFuture<HolderLookup.Provider> registries;
    private static final MapCodec<ResourceKey<Biome>> ENTRY_CODEC = ResourceKey.codec(Registries.BIOME).fieldOf("biome");
    private static final Codec<Climate.ParameterList<ResourceKey<Biome>>> CODEC = Climate.ParameterList.codec(ENTRY_CODEC).fieldOf("biomes").codec();

    public BiomeParametersDumpReport(PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        this.topPath = output.getOutputFolder(PackOutput.Target.REPORTS).resolve("biome_parameters");
        this.registries = registryLookupFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        return this.registries
            .thenCompose(
                lookup -> {
                    DynamicOps<JsonElement> dynamicOps = lookup.createSerializationContext(JsonOps.INSTANCE);
                    List<CompletableFuture<?>> list = new ArrayList<>();
                    MultiNoiseBiomeSourceParameterList.knownPresets()
                        .forEach(
                            (preset, entries) -> list.add(
                                    dumpValue(this.createPath(preset.id()), writer, dynamicOps, CODEC, (Climate.ParameterList<ResourceKey<Biome>>)entries)
                                )
                        );
                    return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
                }
            );
    }

    private static <E> CompletableFuture<?> dumpValue(Path path, CachedOutput writer, DynamicOps<JsonElement> ops, Encoder<E> codec, E biomeSource) {
        Optional<JsonElement> optional = codec.encodeStart(ops, biomeSource)
            .resultOrPartial(error -> LOGGER.error("Couldn't serialize element {}: {}", path, error));
        return optional.isPresent() ? DataProvider.saveStable(writer, optional.get(), path) : CompletableFuture.completedFuture(null);
    }

    private Path createPath(ResourceLocation id) {
        return this.topPath.resolve(id.getNamespace()).resolve(id.getPath() + ".json");
    }

    @Override
    public final String getName() {
        return "Biome Parameters";
    }
}
