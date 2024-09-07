package net.minecraft.world.item.armortrim;

import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TrimPatterns {
    public static final ResourceKey<TrimPattern> SENTRY = registryKey("sentry");
    public static final ResourceKey<TrimPattern> DUNE = registryKey("dune");
    public static final ResourceKey<TrimPattern> COAST = registryKey("coast");
    public static final ResourceKey<TrimPattern> WILD = registryKey("wild");
    public static final ResourceKey<TrimPattern> WARD = registryKey("ward");
    public static final ResourceKey<TrimPattern> EYE = registryKey("eye");
    public static final ResourceKey<TrimPattern> VEX = registryKey("vex");
    public static final ResourceKey<TrimPattern> TIDE = registryKey("tide");
    public static final ResourceKey<TrimPattern> SNOUT = registryKey("snout");
    public static final ResourceKey<TrimPattern> RIB = registryKey("rib");
    public static final ResourceKey<TrimPattern> SPIRE = registryKey("spire");
    public static final ResourceKey<TrimPattern> WAYFINDER = registryKey("wayfinder");
    public static final ResourceKey<TrimPattern> SHAPER = registryKey("shaper");
    public static final ResourceKey<TrimPattern> SILENCE = registryKey("silence");
    public static final ResourceKey<TrimPattern> RAISER = registryKey("raiser");
    public static final ResourceKey<TrimPattern> HOST = registryKey("host");
    public static final ResourceKey<TrimPattern> FLOW = registryKey("flow");
    public static final ResourceKey<TrimPattern> BOLT = registryKey("bolt");

    public static void bootstrap(BootstrapContext<TrimPattern> registry) {
        register(registry, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, SENTRY);
        register(registry, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, DUNE);
        register(registry, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, COAST);
        register(registry, Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, WILD);
        register(registry, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, WARD);
        register(registry, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, EYE);
        register(registry, Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, VEX);
        register(registry, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, TIDE);
        register(registry, Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, SNOUT);
        register(registry, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, RIB);
        register(registry, Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, SPIRE);
        register(registry, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, WAYFINDER);
        register(registry, Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, SHAPER);
        register(registry, Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, SILENCE);
        register(registry, Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, RAISER);
        register(registry, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, HOST);
        register(registry, Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, FLOW);
        register(registry, Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, BOLT);
    }

    public static Optional<Holder.Reference<TrimPattern>> getFromTemplate(HolderLookup.Provider registriesLookup, ItemStack stack) {
        return registriesLookup.lookupOrThrow(Registries.TRIM_PATTERN).listElements().filter(pattern -> stack.is(pattern.value().templateItem())).findFirst();
    }

    public static void register(BootstrapContext<TrimPattern> registry, Item template, ResourceKey<TrimPattern> key) {
        TrimPattern trimPattern = new TrimPattern(
            key.location(),
            BuiltInRegistries.ITEM.wrapAsHolder(template),
            Component.translatable(Util.makeDescriptionId("trim_pattern", key.location())),
            false
        );
        registry.register(key, trimPattern);
    }

    private static ResourceKey<TrimPattern> registryKey(String id) {
        return ResourceKey.create(Registries.TRIM_PATTERN, ResourceLocation.withDefaultNamespace(id));
    }
}
