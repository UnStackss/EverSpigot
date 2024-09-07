package net.minecraft.world.item.armortrim;

import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TrimMaterials {
    public static final ResourceKey<TrimMaterial> QUARTZ = registryKey("quartz");
    public static final ResourceKey<TrimMaterial> IRON = registryKey("iron");
    public static final ResourceKey<TrimMaterial> NETHERITE = registryKey("netherite");
    public static final ResourceKey<TrimMaterial> REDSTONE = registryKey("redstone");
    public static final ResourceKey<TrimMaterial> COPPER = registryKey("copper");
    public static final ResourceKey<TrimMaterial> GOLD = registryKey("gold");
    public static final ResourceKey<TrimMaterial> EMERALD = registryKey("emerald");
    public static final ResourceKey<TrimMaterial> DIAMOND = registryKey("diamond");
    public static final ResourceKey<TrimMaterial> LAPIS = registryKey("lapis");
    public static final ResourceKey<TrimMaterial> AMETHYST = registryKey("amethyst");

    public static void bootstrap(BootstrapContext<TrimMaterial> registry) {
        register(registry, QUARTZ, Items.QUARTZ, Style.EMPTY.withColor(14931140), 0.1F);
        register(registry, IRON, Items.IRON_INGOT, Style.EMPTY.withColor(15527148), 0.2F, Map.of(ArmorMaterials.IRON, "iron_darker"));
        register(registry, NETHERITE, Items.NETHERITE_INGOT, Style.EMPTY.withColor(6445145), 0.3F, Map.of(ArmorMaterials.NETHERITE, "netherite_darker"));
        register(registry, REDSTONE, Items.REDSTONE, Style.EMPTY.withColor(9901575), 0.4F);
        register(registry, COPPER, Items.COPPER_INGOT, Style.EMPTY.withColor(11823181), 0.5F);
        register(registry, GOLD, Items.GOLD_INGOT, Style.EMPTY.withColor(14594349), 0.6F, Map.of(ArmorMaterials.GOLD, "gold_darker"));
        register(registry, EMERALD, Items.EMERALD, Style.EMPTY.withColor(1155126), 0.7F);
        register(registry, DIAMOND, Items.DIAMOND, Style.EMPTY.withColor(7269586), 0.8F, Map.of(ArmorMaterials.DIAMOND, "diamond_darker"));
        register(registry, LAPIS, Items.LAPIS_LAZULI, Style.EMPTY.withColor(4288151), 0.9F);
        register(registry, AMETHYST, Items.AMETHYST_SHARD, Style.EMPTY.withColor(10116294), 1.0F);
    }

    public static Optional<Holder.Reference<TrimMaterial>> getFromIngredient(HolderLookup.Provider registriesLookup, ItemStack stack) {
        return registriesLookup.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().filter(recipe -> stack.is(recipe.value().ingredient())).findFirst();
    }

    private static void register(BootstrapContext<TrimMaterial> registry, ResourceKey<TrimMaterial> key, Item ingredient, Style style, float itemModelIndex) {
        register(registry, key, ingredient, style, itemModelIndex, Map.of());
    }

    private static void register(
        BootstrapContext<TrimMaterial> registry,
        ResourceKey<TrimMaterial> key,
        Item ingredient,
        Style style,
        float itemModelIndex,
        Map<Holder<ArmorMaterial>, String> overrideArmorMaterials
    ) {
        TrimMaterial trimMaterial = TrimMaterial.create(
            key.location().getPath(),
            ingredient,
            itemModelIndex,
            Component.translatable(Util.makeDescriptionId("trim_material", key.location())).withStyle(style),
            overrideArmorMaterials
        );
        registry.register(key, trimMaterial);
    }

    private static ResourceKey<TrimMaterial> registryKey(String id) {
        return ResourceKey.create(Registries.TRIM_MATERIAL, ResourceLocation.withDefaultNamespace(id));
    }
}
