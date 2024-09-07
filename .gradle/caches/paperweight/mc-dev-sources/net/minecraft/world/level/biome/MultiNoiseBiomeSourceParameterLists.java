package net.minecraft.world.level.biome;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class MultiNoiseBiomeSourceParameterLists {
    public static final ResourceKey<MultiNoiseBiomeSourceParameterList> NETHER = register("nether");
    public static final ResourceKey<MultiNoiseBiomeSourceParameterList> OVERWORLD = register("overworld");

    public static void bootstrap(BootstrapContext<MultiNoiseBiomeSourceParameterList> registry) {
        HolderGetter<Biome> holderGetter = registry.lookup(Registries.BIOME);
        registry.register(NETHER, new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.NETHER, holderGetter));
        registry.register(OVERWORLD, new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, holderGetter));
    }

    private static ResourceKey<MultiNoiseBiomeSourceParameterList> register(String id) {
        return ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, ResourceLocation.withDefaultNamespace(id));
    }
}
