package net.minecraft.world.entity.animal;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

public class WolfVariants {
    public static final ResourceKey<WolfVariant> PALE = createKey("pale");
    public static final ResourceKey<WolfVariant> SPOTTED = createKey("spotted");
    public static final ResourceKey<WolfVariant> SNOWY = createKey("snowy");
    public static final ResourceKey<WolfVariant> BLACK = createKey("black");
    public static final ResourceKey<WolfVariant> ASHEN = createKey("ashen");
    public static final ResourceKey<WolfVariant> RUSTY = createKey("rusty");
    public static final ResourceKey<WolfVariant> WOODS = createKey("woods");
    public static final ResourceKey<WolfVariant> CHESTNUT = createKey("chestnut");
    public static final ResourceKey<WolfVariant> STRIPED = createKey("striped");
    public static final ResourceKey<WolfVariant> DEFAULT = PALE;

    private static ResourceKey<WolfVariant> createKey(String id) {
        return ResourceKey.create(Registries.WOLF_VARIANT, ResourceLocation.withDefaultNamespace(id));
    }

    static void register(BootstrapContext<WolfVariant> registry, ResourceKey<WolfVariant> key, String textureName, ResourceKey<Biome> biome) {
        register(registry, key, textureName, HolderSet.direct(registry.lookup(Registries.BIOME).getOrThrow(biome)));
    }

    static void register(BootstrapContext<WolfVariant> registry, ResourceKey<WolfVariant> key, String textureName, TagKey<Biome> biomeTag) {
        register(registry, key, textureName, registry.lookup(Registries.BIOME).getOrThrow(biomeTag));
    }

    static void register(BootstrapContext<WolfVariant> registry, ResourceKey<WolfVariant> key, String textureName, HolderSet<Biome> biomes) {
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace("entity/wolf/" + textureName);
        ResourceLocation resourceLocation2 = ResourceLocation.withDefaultNamespace("entity/wolf/" + textureName + "_tame");
        ResourceLocation resourceLocation3 = ResourceLocation.withDefaultNamespace("entity/wolf/" + textureName + "_angry");
        registry.register(key, new WolfVariant(resourceLocation, resourceLocation2, resourceLocation3, biomes));
    }

    public static Holder<WolfVariant> getSpawnVariant(RegistryAccess dynamicRegistryManager, Holder<Biome> biome) {
        Registry<WolfVariant> registry = dynamicRegistryManager.registryOrThrow(Registries.WOLF_VARIANT);
        return registry.holders()
            .filter(entry -> entry.value().biomes().contains(biome))
            .findFirst()
            .or(() -> registry.getHolder(DEFAULT))
            .or(registry::getAny)
            .orElseThrow();
    }

    public static void bootstrap(BootstrapContext<WolfVariant> registry) {
        register(registry, PALE, "wolf", Biomes.TAIGA);
        register(registry, SPOTTED, "wolf_spotted", BiomeTags.IS_SAVANNA);
        register(registry, SNOWY, "wolf_snowy", Biomes.GROVE);
        register(registry, BLACK, "wolf_black", Biomes.OLD_GROWTH_PINE_TAIGA);
        register(registry, ASHEN, "wolf_ashen", Biomes.SNOWY_TAIGA);
        register(registry, RUSTY, "wolf_rusty", BiomeTags.IS_JUNGLE);
        register(registry, WOODS, "wolf_woods", Biomes.FOREST);
        register(registry, CHESTNUT, "wolf_chestnut", Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        register(registry, STRIPED, "wolf_striped", BiomeTags.IS_BADLANDS);
    }
}
