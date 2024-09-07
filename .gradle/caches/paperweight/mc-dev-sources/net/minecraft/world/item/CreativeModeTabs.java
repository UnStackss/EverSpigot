package net.minecraft.world.item;

import com.mojang.datafixers.util.Pair;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SuspiciousEffectHolder;

public class CreativeModeTabs {
    private static final ResourceLocation INVENTORY_BACKGROUND = CreativeModeTab.createTextureLocation("inventory");
    private static final ResourceLocation SEARCH_BACKGROUND = CreativeModeTab.createTextureLocation("item_search");
    private static final ResourceKey<CreativeModeTab> BUILDING_BLOCKS = createKey("building_blocks");
    private static final ResourceKey<CreativeModeTab> COLORED_BLOCKS = createKey("colored_blocks");
    private static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS = createKey("natural_blocks");
    private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS = createKey("functional_blocks");
    private static final ResourceKey<CreativeModeTab> REDSTONE_BLOCKS = createKey("redstone_blocks");
    private static final ResourceKey<CreativeModeTab> HOTBAR = createKey("hotbar");
    private static final ResourceKey<CreativeModeTab> SEARCH = createKey("search");
    private static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES = createKey("tools_and_utilities");
    private static final ResourceKey<CreativeModeTab> COMBAT = createKey("combat");
    private static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS = createKey("food_and_drinks");
    private static final ResourceKey<CreativeModeTab> INGREDIENTS = createKey("ingredients");
    private static final ResourceKey<CreativeModeTab> SPAWN_EGGS = createKey("spawn_eggs");
    private static final ResourceKey<CreativeModeTab> OP_BLOCKS = createKey("op_blocks");
    private static final ResourceKey<CreativeModeTab> INVENTORY = createKey("inventory");
    private static final Comparator<Holder<PaintingVariant>> PAINTING_COMPARATOR = Comparator.comparing(
        Holder::value, Comparator.comparingInt(PaintingVariant::area).thenComparing(PaintingVariant::width)
    );
    @Nullable
    private static CreativeModeTab.ItemDisplayParameters CACHED_PARAMETERS;

    private static ResourceKey<CreativeModeTab> createKey(String id) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(id));
    }

    public static CreativeModeTab bootstrap(Registry<CreativeModeTab> registry) {
        Registry.register(
            registry,
            BUILDING_BLOCKS,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.buildingBlocks"))
                .icon(() -> new ItemStack(Blocks.BRICKS))
                .displayItems((displayContext, entries) -> {
                    entries.accept(Items.OAK_LOG);
                    entries.accept(Items.OAK_WOOD);
                    entries.accept(Items.STRIPPED_OAK_LOG);
                    entries.accept(Items.STRIPPED_OAK_WOOD);
                    entries.accept(Items.OAK_PLANKS);
                    entries.accept(Items.OAK_STAIRS);
                    entries.accept(Items.OAK_SLAB);
                    entries.accept(Items.OAK_FENCE);
                    entries.accept(Items.OAK_FENCE_GATE);
                    entries.accept(Items.OAK_DOOR);
                    entries.accept(Items.OAK_TRAPDOOR);
                    entries.accept(Items.OAK_PRESSURE_PLATE);
                    entries.accept(Items.OAK_BUTTON);
                    entries.accept(Items.SPRUCE_LOG);
                    entries.accept(Items.SPRUCE_WOOD);
                    entries.accept(Items.STRIPPED_SPRUCE_LOG);
                    entries.accept(Items.STRIPPED_SPRUCE_WOOD);
                    entries.accept(Items.SPRUCE_PLANKS);
                    entries.accept(Items.SPRUCE_STAIRS);
                    entries.accept(Items.SPRUCE_SLAB);
                    entries.accept(Items.SPRUCE_FENCE);
                    entries.accept(Items.SPRUCE_FENCE_GATE);
                    entries.accept(Items.SPRUCE_DOOR);
                    entries.accept(Items.SPRUCE_TRAPDOOR);
                    entries.accept(Items.SPRUCE_PRESSURE_PLATE);
                    entries.accept(Items.SPRUCE_BUTTON);
                    entries.accept(Items.BIRCH_LOG);
                    entries.accept(Items.BIRCH_WOOD);
                    entries.accept(Items.STRIPPED_BIRCH_LOG);
                    entries.accept(Items.STRIPPED_BIRCH_WOOD);
                    entries.accept(Items.BIRCH_PLANKS);
                    entries.accept(Items.BIRCH_STAIRS);
                    entries.accept(Items.BIRCH_SLAB);
                    entries.accept(Items.BIRCH_FENCE);
                    entries.accept(Items.BIRCH_FENCE_GATE);
                    entries.accept(Items.BIRCH_DOOR);
                    entries.accept(Items.BIRCH_TRAPDOOR);
                    entries.accept(Items.BIRCH_PRESSURE_PLATE);
                    entries.accept(Items.BIRCH_BUTTON);
                    entries.accept(Items.JUNGLE_LOG);
                    entries.accept(Items.JUNGLE_WOOD);
                    entries.accept(Items.STRIPPED_JUNGLE_LOG);
                    entries.accept(Items.STRIPPED_JUNGLE_WOOD);
                    entries.accept(Items.JUNGLE_PLANKS);
                    entries.accept(Items.JUNGLE_STAIRS);
                    entries.accept(Items.JUNGLE_SLAB);
                    entries.accept(Items.JUNGLE_FENCE);
                    entries.accept(Items.JUNGLE_FENCE_GATE);
                    entries.accept(Items.JUNGLE_DOOR);
                    entries.accept(Items.JUNGLE_TRAPDOOR);
                    entries.accept(Items.JUNGLE_PRESSURE_PLATE);
                    entries.accept(Items.JUNGLE_BUTTON);
                    entries.accept(Items.ACACIA_LOG);
                    entries.accept(Items.ACACIA_WOOD);
                    entries.accept(Items.STRIPPED_ACACIA_LOG);
                    entries.accept(Items.STRIPPED_ACACIA_WOOD);
                    entries.accept(Items.ACACIA_PLANKS);
                    entries.accept(Items.ACACIA_STAIRS);
                    entries.accept(Items.ACACIA_SLAB);
                    entries.accept(Items.ACACIA_FENCE);
                    entries.accept(Items.ACACIA_FENCE_GATE);
                    entries.accept(Items.ACACIA_DOOR);
                    entries.accept(Items.ACACIA_TRAPDOOR);
                    entries.accept(Items.ACACIA_PRESSURE_PLATE);
                    entries.accept(Items.ACACIA_BUTTON);
                    entries.accept(Items.DARK_OAK_LOG);
                    entries.accept(Items.DARK_OAK_WOOD);
                    entries.accept(Items.STRIPPED_DARK_OAK_LOG);
                    entries.accept(Items.STRIPPED_DARK_OAK_WOOD);
                    entries.accept(Items.DARK_OAK_PLANKS);
                    entries.accept(Items.DARK_OAK_STAIRS);
                    entries.accept(Items.DARK_OAK_SLAB);
                    entries.accept(Items.DARK_OAK_FENCE);
                    entries.accept(Items.DARK_OAK_FENCE_GATE);
                    entries.accept(Items.DARK_OAK_DOOR);
                    entries.accept(Items.DARK_OAK_TRAPDOOR);
                    entries.accept(Items.DARK_OAK_PRESSURE_PLATE);
                    entries.accept(Items.DARK_OAK_BUTTON);
                    entries.accept(Items.MANGROVE_LOG);
                    entries.accept(Items.MANGROVE_WOOD);
                    entries.accept(Items.STRIPPED_MANGROVE_LOG);
                    entries.accept(Items.STRIPPED_MANGROVE_WOOD);
                    entries.accept(Items.MANGROVE_PLANKS);
                    entries.accept(Items.MANGROVE_STAIRS);
                    entries.accept(Items.MANGROVE_SLAB);
                    entries.accept(Items.MANGROVE_FENCE);
                    entries.accept(Items.MANGROVE_FENCE_GATE);
                    entries.accept(Items.MANGROVE_DOOR);
                    entries.accept(Items.MANGROVE_TRAPDOOR);
                    entries.accept(Items.MANGROVE_PRESSURE_PLATE);
                    entries.accept(Items.MANGROVE_BUTTON);
                    entries.accept(Items.CHERRY_LOG);
                    entries.accept(Items.CHERRY_WOOD);
                    entries.accept(Items.STRIPPED_CHERRY_LOG);
                    entries.accept(Items.STRIPPED_CHERRY_WOOD);
                    entries.accept(Items.CHERRY_PLANKS);
                    entries.accept(Items.CHERRY_STAIRS);
                    entries.accept(Items.CHERRY_SLAB);
                    entries.accept(Items.CHERRY_FENCE);
                    entries.accept(Items.CHERRY_FENCE_GATE);
                    entries.accept(Items.CHERRY_DOOR);
                    entries.accept(Items.CHERRY_TRAPDOOR);
                    entries.accept(Items.CHERRY_PRESSURE_PLATE);
                    entries.accept(Items.CHERRY_BUTTON);
                    entries.accept(Items.BAMBOO_BLOCK);
                    entries.accept(Items.STRIPPED_BAMBOO_BLOCK);
                    entries.accept(Items.BAMBOO_PLANKS);
                    entries.accept(Items.BAMBOO_MOSAIC);
                    entries.accept(Items.BAMBOO_STAIRS);
                    entries.accept(Items.BAMBOO_MOSAIC_STAIRS);
                    entries.accept(Items.BAMBOO_SLAB);
                    entries.accept(Items.BAMBOO_MOSAIC_SLAB);
                    entries.accept(Items.BAMBOO_FENCE);
                    entries.accept(Items.BAMBOO_FENCE_GATE);
                    entries.accept(Items.BAMBOO_DOOR);
                    entries.accept(Items.BAMBOO_TRAPDOOR);
                    entries.accept(Items.BAMBOO_PRESSURE_PLATE);
                    entries.accept(Items.BAMBOO_BUTTON);
                    entries.accept(Items.CRIMSON_STEM);
                    entries.accept(Items.CRIMSON_HYPHAE);
                    entries.accept(Items.STRIPPED_CRIMSON_STEM);
                    entries.accept(Items.STRIPPED_CRIMSON_HYPHAE);
                    entries.accept(Items.CRIMSON_PLANKS);
                    entries.accept(Items.CRIMSON_STAIRS);
                    entries.accept(Items.CRIMSON_SLAB);
                    entries.accept(Items.CRIMSON_FENCE);
                    entries.accept(Items.CRIMSON_FENCE_GATE);
                    entries.accept(Items.CRIMSON_DOOR);
                    entries.accept(Items.CRIMSON_TRAPDOOR);
                    entries.accept(Items.CRIMSON_PRESSURE_PLATE);
                    entries.accept(Items.CRIMSON_BUTTON);
                    entries.accept(Items.WARPED_STEM);
                    entries.accept(Items.WARPED_HYPHAE);
                    entries.accept(Items.STRIPPED_WARPED_STEM);
                    entries.accept(Items.STRIPPED_WARPED_HYPHAE);
                    entries.accept(Items.WARPED_PLANKS);
                    entries.accept(Items.WARPED_STAIRS);
                    entries.accept(Items.WARPED_SLAB);
                    entries.accept(Items.WARPED_FENCE);
                    entries.accept(Items.WARPED_FENCE_GATE);
                    entries.accept(Items.WARPED_DOOR);
                    entries.accept(Items.WARPED_TRAPDOOR);
                    entries.accept(Items.WARPED_PRESSURE_PLATE);
                    entries.accept(Items.WARPED_BUTTON);
                    entries.accept(Items.STONE);
                    entries.accept(Items.STONE_STAIRS);
                    entries.accept(Items.STONE_SLAB);
                    entries.accept(Items.STONE_PRESSURE_PLATE);
                    entries.accept(Items.STONE_BUTTON);
                    entries.accept(Items.COBBLESTONE);
                    entries.accept(Items.COBBLESTONE_STAIRS);
                    entries.accept(Items.COBBLESTONE_SLAB);
                    entries.accept(Items.COBBLESTONE_WALL);
                    entries.accept(Items.MOSSY_COBBLESTONE);
                    entries.accept(Items.MOSSY_COBBLESTONE_STAIRS);
                    entries.accept(Items.MOSSY_COBBLESTONE_SLAB);
                    entries.accept(Items.MOSSY_COBBLESTONE_WALL);
                    entries.accept(Items.SMOOTH_STONE);
                    entries.accept(Items.SMOOTH_STONE_SLAB);
                    entries.accept(Items.STONE_BRICKS);
                    entries.accept(Items.CRACKED_STONE_BRICKS);
                    entries.accept(Items.STONE_BRICK_STAIRS);
                    entries.accept(Items.STONE_BRICK_SLAB);
                    entries.accept(Items.STONE_BRICK_WALL);
                    entries.accept(Items.CHISELED_STONE_BRICKS);
                    entries.accept(Items.MOSSY_STONE_BRICKS);
                    entries.accept(Items.MOSSY_STONE_BRICK_STAIRS);
                    entries.accept(Items.MOSSY_STONE_BRICK_SLAB);
                    entries.accept(Items.MOSSY_STONE_BRICK_WALL);
                    entries.accept(Items.GRANITE);
                    entries.accept(Items.GRANITE_STAIRS);
                    entries.accept(Items.GRANITE_SLAB);
                    entries.accept(Items.GRANITE_WALL);
                    entries.accept(Items.POLISHED_GRANITE);
                    entries.accept(Items.POLISHED_GRANITE_STAIRS);
                    entries.accept(Items.POLISHED_GRANITE_SLAB);
                    entries.accept(Items.DIORITE);
                    entries.accept(Items.DIORITE_STAIRS);
                    entries.accept(Items.DIORITE_SLAB);
                    entries.accept(Items.DIORITE_WALL);
                    entries.accept(Items.POLISHED_DIORITE);
                    entries.accept(Items.POLISHED_DIORITE_STAIRS);
                    entries.accept(Items.POLISHED_DIORITE_SLAB);
                    entries.accept(Items.ANDESITE);
                    entries.accept(Items.ANDESITE_STAIRS);
                    entries.accept(Items.ANDESITE_SLAB);
                    entries.accept(Items.ANDESITE_WALL);
                    entries.accept(Items.POLISHED_ANDESITE);
                    entries.accept(Items.POLISHED_ANDESITE_STAIRS);
                    entries.accept(Items.POLISHED_ANDESITE_SLAB);
                    entries.accept(Items.DEEPSLATE);
                    entries.accept(Items.COBBLED_DEEPSLATE);
                    entries.accept(Items.COBBLED_DEEPSLATE_STAIRS);
                    entries.accept(Items.COBBLED_DEEPSLATE_SLAB);
                    entries.accept(Items.COBBLED_DEEPSLATE_WALL);
                    entries.accept(Items.CHISELED_DEEPSLATE);
                    entries.accept(Items.POLISHED_DEEPSLATE);
                    entries.accept(Items.POLISHED_DEEPSLATE_STAIRS);
                    entries.accept(Items.POLISHED_DEEPSLATE_SLAB);
                    entries.accept(Items.POLISHED_DEEPSLATE_WALL);
                    entries.accept(Items.DEEPSLATE_BRICKS);
                    entries.accept(Items.CRACKED_DEEPSLATE_BRICKS);
                    entries.accept(Items.DEEPSLATE_BRICK_STAIRS);
                    entries.accept(Items.DEEPSLATE_BRICK_SLAB);
                    entries.accept(Items.DEEPSLATE_BRICK_WALL);
                    entries.accept(Items.DEEPSLATE_TILES);
                    entries.accept(Items.CRACKED_DEEPSLATE_TILES);
                    entries.accept(Items.DEEPSLATE_TILE_STAIRS);
                    entries.accept(Items.DEEPSLATE_TILE_SLAB);
                    entries.accept(Items.DEEPSLATE_TILE_WALL);
                    entries.accept(Items.REINFORCED_DEEPSLATE);
                    entries.accept(Items.TUFF);
                    entries.accept(Items.TUFF_STAIRS);
                    entries.accept(Items.TUFF_SLAB);
                    entries.accept(Items.TUFF_WALL);
                    entries.accept(Items.CHISELED_TUFF);
                    entries.accept(Items.POLISHED_TUFF);
                    entries.accept(Items.POLISHED_TUFF_STAIRS);
                    entries.accept(Items.POLISHED_TUFF_SLAB);
                    entries.accept(Items.POLISHED_TUFF_WALL);
                    entries.accept(Items.TUFF_BRICKS);
                    entries.accept(Items.TUFF_BRICK_STAIRS);
                    entries.accept(Items.TUFF_BRICK_SLAB);
                    entries.accept(Items.TUFF_BRICK_WALL);
                    entries.accept(Items.CHISELED_TUFF_BRICKS);
                    entries.accept(Items.BRICKS);
                    entries.accept(Items.BRICK_STAIRS);
                    entries.accept(Items.BRICK_SLAB);
                    entries.accept(Items.BRICK_WALL);
                    entries.accept(Items.PACKED_MUD);
                    entries.accept(Items.MUD_BRICKS);
                    entries.accept(Items.MUD_BRICK_STAIRS);
                    entries.accept(Items.MUD_BRICK_SLAB);
                    entries.accept(Items.MUD_BRICK_WALL);
                    entries.accept(Items.SANDSTONE);
                    entries.accept(Items.SANDSTONE_STAIRS);
                    entries.accept(Items.SANDSTONE_SLAB);
                    entries.accept(Items.SANDSTONE_WALL);
                    entries.accept(Items.CHISELED_SANDSTONE);
                    entries.accept(Items.SMOOTH_SANDSTONE);
                    entries.accept(Items.SMOOTH_SANDSTONE_STAIRS);
                    entries.accept(Items.SMOOTH_SANDSTONE_SLAB);
                    entries.accept(Items.CUT_SANDSTONE);
                    entries.accept(Items.CUT_STANDSTONE_SLAB);
                    entries.accept(Items.RED_SANDSTONE);
                    entries.accept(Items.RED_SANDSTONE_STAIRS);
                    entries.accept(Items.RED_SANDSTONE_SLAB);
                    entries.accept(Items.RED_SANDSTONE_WALL);
                    entries.accept(Items.CHISELED_RED_SANDSTONE);
                    entries.accept(Items.SMOOTH_RED_SANDSTONE);
                    entries.accept(Items.SMOOTH_RED_SANDSTONE_STAIRS);
                    entries.accept(Items.SMOOTH_RED_SANDSTONE_SLAB);
                    entries.accept(Items.CUT_RED_SANDSTONE);
                    entries.accept(Items.CUT_RED_SANDSTONE_SLAB);
                    entries.accept(Items.SEA_LANTERN);
                    entries.accept(Items.PRISMARINE);
                    entries.accept(Items.PRISMARINE_STAIRS);
                    entries.accept(Items.PRISMARINE_SLAB);
                    entries.accept(Items.PRISMARINE_WALL);
                    entries.accept(Items.PRISMARINE_BRICKS);
                    entries.accept(Items.PRISMARINE_BRICK_STAIRS);
                    entries.accept(Items.PRISMARINE_BRICK_SLAB);
                    entries.accept(Items.DARK_PRISMARINE);
                    entries.accept(Items.DARK_PRISMARINE_STAIRS);
                    entries.accept(Items.DARK_PRISMARINE_SLAB);
                    entries.accept(Items.NETHERRACK);
                    entries.accept(Items.NETHER_BRICKS);
                    entries.accept(Items.CRACKED_NETHER_BRICKS);
                    entries.accept(Items.NETHER_BRICK_STAIRS);
                    entries.accept(Items.NETHER_BRICK_SLAB);
                    entries.accept(Items.NETHER_BRICK_WALL);
                    entries.accept(Items.NETHER_BRICK_FENCE);
                    entries.accept(Items.CHISELED_NETHER_BRICKS);
                    entries.accept(Items.RED_NETHER_BRICKS);
                    entries.accept(Items.RED_NETHER_BRICK_STAIRS);
                    entries.accept(Items.RED_NETHER_BRICK_SLAB);
                    entries.accept(Items.RED_NETHER_BRICK_WALL);
                    entries.accept(Items.BASALT);
                    entries.accept(Items.SMOOTH_BASALT);
                    entries.accept(Items.POLISHED_BASALT);
                    entries.accept(Items.BLACKSTONE);
                    entries.accept(Items.GILDED_BLACKSTONE);
                    entries.accept(Items.BLACKSTONE_STAIRS);
                    entries.accept(Items.BLACKSTONE_SLAB);
                    entries.accept(Items.BLACKSTONE_WALL);
                    entries.accept(Items.CHISELED_POLISHED_BLACKSTONE);
                    entries.accept(Items.POLISHED_BLACKSTONE);
                    entries.accept(Items.POLISHED_BLACKSTONE_STAIRS);
                    entries.accept(Items.POLISHED_BLACKSTONE_SLAB);
                    entries.accept(Items.POLISHED_BLACKSTONE_WALL);
                    entries.accept(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE);
                    entries.accept(Items.POLISHED_BLACKSTONE_BUTTON);
                    entries.accept(Items.POLISHED_BLACKSTONE_BRICKS);
                    entries.accept(Items.CRACKED_POLISHED_BLACKSTONE_BRICKS);
                    entries.accept(Items.POLISHED_BLACKSTONE_BRICK_STAIRS);
                    entries.accept(Items.POLISHED_BLACKSTONE_BRICK_SLAB);
                    entries.accept(Items.POLISHED_BLACKSTONE_BRICK_WALL);
                    entries.accept(Items.END_STONE);
                    entries.accept(Items.END_STONE_BRICKS);
                    entries.accept(Items.END_STONE_BRICK_STAIRS);
                    entries.accept(Items.END_STONE_BRICK_SLAB);
                    entries.accept(Items.END_STONE_BRICK_WALL);
                    entries.accept(Items.PURPUR_BLOCK);
                    entries.accept(Items.PURPUR_PILLAR);
                    entries.accept(Items.PURPUR_STAIRS);
                    entries.accept(Items.PURPUR_SLAB);
                    entries.accept(Items.COAL_BLOCK);
                    entries.accept(Items.IRON_BLOCK);
                    entries.accept(Items.IRON_BARS);
                    entries.accept(Items.IRON_DOOR);
                    entries.accept(Items.IRON_TRAPDOOR);
                    entries.accept(Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
                    entries.accept(Items.CHAIN);
                    entries.accept(Items.GOLD_BLOCK);
                    entries.accept(Items.LIGHT_WEIGHTED_PRESSURE_PLATE);
                    entries.accept(Items.REDSTONE_BLOCK);
                    entries.accept(Items.EMERALD_BLOCK);
                    entries.accept(Items.LAPIS_BLOCK);
                    entries.accept(Items.DIAMOND_BLOCK);
                    entries.accept(Items.NETHERITE_BLOCK);
                    entries.accept(Items.QUARTZ_BLOCK);
                    entries.accept(Items.QUARTZ_STAIRS);
                    entries.accept(Items.QUARTZ_SLAB);
                    entries.accept(Items.CHISELED_QUARTZ_BLOCK);
                    entries.accept(Items.QUARTZ_BRICKS);
                    entries.accept(Items.QUARTZ_PILLAR);
                    entries.accept(Items.SMOOTH_QUARTZ);
                    entries.accept(Items.SMOOTH_QUARTZ_STAIRS);
                    entries.accept(Items.SMOOTH_QUARTZ_SLAB);
                    entries.accept(Items.AMETHYST_BLOCK);
                    entries.accept(Items.COPPER_BLOCK);
                    entries.accept(Items.CHISELED_COPPER);
                    entries.accept(Items.COPPER_GRATE);
                    entries.accept(Items.CUT_COPPER);
                    entries.accept(Items.CUT_COPPER_STAIRS);
                    entries.accept(Items.CUT_COPPER_SLAB);
                    entries.accept(Items.COPPER_DOOR);
                    entries.accept(Items.COPPER_TRAPDOOR);
                    entries.accept(Items.COPPER_BULB);
                    entries.accept(Items.EXPOSED_COPPER);
                    entries.accept(Items.EXPOSED_CHISELED_COPPER);
                    entries.accept(Items.EXPOSED_COPPER_GRATE);
                    entries.accept(Items.EXPOSED_CUT_COPPER);
                    entries.accept(Items.EXPOSED_CUT_COPPER_STAIRS);
                    entries.accept(Items.EXPOSED_CUT_COPPER_SLAB);
                    entries.accept(Items.EXPOSED_COPPER_DOOR);
                    entries.accept(Items.EXPOSED_COPPER_TRAPDOOR);
                    entries.accept(Items.EXPOSED_COPPER_BULB);
                    entries.accept(Items.WEATHERED_COPPER);
                    entries.accept(Items.WEATHERED_CHISELED_COPPER);
                    entries.accept(Items.WEATHERED_COPPER_GRATE);
                    entries.accept(Items.WEATHERED_CUT_COPPER);
                    entries.accept(Items.WEATHERED_CUT_COPPER_STAIRS);
                    entries.accept(Items.WEATHERED_CUT_COPPER_SLAB);
                    entries.accept(Items.WEATHERED_COPPER_DOOR);
                    entries.accept(Items.WEATHERED_COPPER_TRAPDOOR);
                    entries.accept(Items.WEATHERED_COPPER_BULB);
                    entries.accept(Items.OXIDIZED_COPPER);
                    entries.accept(Items.OXIDIZED_CHISELED_COPPER);
                    entries.accept(Items.OXIDIZED_COPPER_GRATE);
                    entries.accept(Items.OXIDIZED_CUT_COPPER);
                    entries.accept(Items.OXIDIZED_CUT_COPPER_STAIRS);
                    entries.accept(Items.OXIDIZED_CUT_COPPER_SLAB);
                    entries.accept(Items.OXIDIZED_COPPER_DOOR);
                    entries.accept(Items.OXIDIZED_COPPER_TRAPDOOR);
                    entries.accept(Items.OXIDIZED_COPPER_BULB);
                    entries.accept(Items.WAXED_COPPER_BLOCK);
                    entries.accept(Items.WAXED_CHISELED_COPPER);
                    entries.accept(Items.WAXED_COPPER_GRATE);
                    entries.accept(Items.WAXED_CUT_COPPER);
                    entries.accept(Items.WAXED_CUT_COPPER_STAIRS);
                    entries.accept(Items.WAXED_CUT_COPPER_SLAB);
                    entries.accept(Items.WAXED_COPPER_DOOR);
                    entries.accept(Items.WAXED_COPPER_TRAPDOOR);
                    entries.accept(Items.WAXED_COPPER_BULB);
                    entries.accept(Items.WAXED_EXPOSED_COPPER);
                    entries.accept(Items.WAXED_EXPOSED_CHISELED_COPPER);
                    entries.accept(Items.WAXED_EXPOSED_COPPER_GRATE);
                    entries.accept(Items.WAXED_EXPOSED_CUT_COPPER);
                    entries.accept(Items.WAXED_EXPOSED_CUT_COPPER_STAIRS);
                    entries.accept(Items.WAXED_EXPOSED_CUT_COPPER_SLAB);
                    entries.accept(Items.WAXED_EXPOSED_COPPER_DOOR);
                    entries.accept(Items.WAXED_EXPOSED_COPPER_TRAPDOOR);
                    entries.accept(Items.WAXED_EXPOSED_COPPER_BULB);
                    entries.accept(Items.WAXED_WEATHERED_COPPER);
                    entries.accept(Items.WAXED_WEATHERED_CHISELED_COPPER);
                    entries.accept(Items.WAXED_WEATHERED_COPPER_GRATE);
                    entries.accept(Items.WAXED_WEATHERED_CUT_COPPER);
                    entries.accept(Items.WAXED_WEATHERED_CUT_COPPER_STAIRS);
                    entries.accept(Items.WAXED_WEATHERED_CUT_COPPER_SLAB);
                    entries.accept(Items.WAXED_WEATHERED_COPPER_DOOR);
                    entries.accept(Items.WAXED_WEATHERED_COPPER_TRAPDOOR);
                    entries.accept(Items.WAXED_WEATHERED_COPPER_BULB);
                    entries.accept(Items.WAXED_OXIDIZED_COPPER);
                    entries.accept(Items.WAXED_OXIDIZED_CHISELED_COPPER);
                    entries.accept(Items.WAXED_OXIDIZED_COPPER_GRATE);
                    entries.accept(Items.WAXED_OXIDIZED_CUT_COPPER);
                    entries.accept(Items.WAXED_OXIDIZED_CUT_COPPER_STAIRS);
                    entries.accept(Items.WAXED_OXIDIZED_CUT_COPPER_SLAB);
                    entries.accept(Items.WAXED_OXIDIZED_COPPER_DOOR);
                    entries.accept(Items.WAXED_OXIDIZED_COPPER_TRAPDOOR);
                    entries.accept(Items.WAXED_OXIDIZED_COPPER_BULB);
                })
                .build()
        );
        Registry.register(
            registry,
            COLORED_BLOCKS,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
                .title(Component.translatable("itemGroup.coloredBlocks"))
                .icon(() -> new ItemStack(Blocks.CYAN_WOOL))
                .displayItems((displayContext, entries) -> {
                    entries.accept(Items.WHITE_WOOL);
                    entries.accept(Items.LIGHT_GRAY_WOOL);
                    entries.accept(Items.GRAY_WOOL);
                    entries.accept(Items.BLACK_WOOL);
                    entries.accept(Items.BROWN_WOOL);
                    entries.accept(Items.RED_WOOL);
                    entries.accept(Items.ORANGE_WOOL);
                    entries.accept(Items.YELLOW_WOOL);
                    entries.accept(Items.LIME_WOOL);
                    entries.accept(Items.GREEN_WOOL);
                    entries.accept(Items.CYAN_WOOL);
                    entries.accept(Items.LIGHT_BLUE_WOOL);
                    entries.accept(Items.BLUE_WOOL);
                    entries.accept(Items.PURPLE_WOOL);
                    entries.accept(Items.MAGENTA_WOOL);
                    entries.accept(Items.PINK_WOOL);
                    entries.accept(Items.WHITE_CARPET);
                    entries.accept(Items.LIGHT_GRAY_CARPET);
                    entries.accept(Items.GRAY_CARPET);
                    entries.accept(Items.BLACK_CARPET);
                    entries.accept(Items.BROWN_CARPET);
                    entries.accept(Items.RED_CARPET);
                    entries.accept(Items.ORANGE_CARPET);
                    entries.accept(Items.YELLOW_CARPET);
                    entries.accept(Items.LIME_CARPET);
                    entries.accept(Items.GREEN_CARPET);
                    entries.accept(Items.CYAN_CARPET);
                    entries.accept(Items.LIGHT_BLUE_CARPET);
                    entries.accept(Items.BLUE_CARPET);
                    entries.accept(Items.PURPLE_CARPET);
                    entries.accept(Items.MAGENTA_CARPET);
                    entries.accept(Items.PINK_CARPET);
                    entries.accept(Items.TERRACOTTA);
                    entries.accept(Items.WHITE_TERRACOTTA);
                    entries.accept(Items.LIGHT_GRAY_TERRACOTTA);
                    entries.accept(Items.GRAY_TERRACOTTA);
                    entries.accept(Items.BLACK_TERRACOTTA);
                    entries.accept(Items.BROWN_TERRACOTTA);
                    entries.accept(Items.RED_TERRACOTTA);
                    entries.accept(Items.ORANGE_TERRACOTTA);
                    entries.accept(Items.YELLOW_TERRACOTTA);
                    entries.accept(Items.LIME_TERRACOTTA);
                    entries.accept(Items.GREEN_TERRACOTTA);
                    entries.accept(Items.CYAN_TERRACOTTA);
                    entries.accept(Items.LIGHT_BLUE_TERRACOTTA);
                    entries.accept(Items.BLUE_TERRACOTTA);
                    entries.accept(Items.PURPLE_TERRACOTTA);
                    entries.accept(Items.MAGENTA_TERRACOTTA);
                    entries.accept(Items.PINK_TERRACOTTA);
                    entries.accept(Items.WHITE_CONCRETE);
                    entries.accept(Items.LIGHT_GRAY_CONCRETE);
                    entries.accept(Items.GRAY_CONCRETE);
                    entries.accept(Items.BLACK_CONCRETE);
                    entries.accept(Items.BROWN_CONCRETE);
                    entries.accept(Items.RED_CONCRETE);
                    entries.accept(Items.ORANGE_CONCRETE);
                    entries.accept(Items.YELLOW_CONCRETE);
                    entries.accept(Items.LIME_CONCRETE);
                    entries.accept(Items.GREEN_CONCRETE);
                    entries.accept(Items.CYAN_CONCRETE);
                    entries.accept(Items.LIGHT_BLUE_CONCRETE);
                    entries.accept(Items.BLUE_CONCRETE);
                    entries.accept(Items.PURPLE_CONCRETE);
                    entries.accept(Items.MAGENTA_CONCRETE);
                    entries.accept(Items.PINK_CONCRETE);
                    entries.accept(Items.WHITE_CONCRETE_POWDER);
                    entries.accept(Items.LIGHT_GRAY_CONCRETE_POWDER);
                    entries.accept(Items.GRAY_CONCRETE_POWDER);
                    entries.accept(Items.BLACK_CONCRETE_POWDER);
                    entries.accept(Items.BROWN_CONCRETE_POWDER);
                    entries.accept(Items.RED_CONCRETE_POWDER);
                    entries.accept(Items.ORANGE_CONCRETE_POWDER);
                    entries.accept(Items.YELLOW_CONCRETE_POWDER);
                    entries.accept(Items.LIME_CONCRETE_POWDER);
                    entries.accept(Items.GREEN_CONCRETE_POWDER);
                    entries.accept(Items.CYAN_CONCRETE_POWDER);
                    entries.accept(Items.LIGHT_BLUE_CONCRETE_POWDER);
                    entries.accept(Items.BLUE_CONCRETE_POWDER);
                    entries.accept(Items.PURPLE_CONCRETE_POWDER);
                    entries.accept(Items.MAGENTA_CONCRETE_POWDER);
                    entries.accept(Items.PINK_CONCRETE_POWDER);
                    entries.accept(Items.WHITE_GLAZED_TERRACOTTA);
                    entries.accept(Items.LIGHT_GRAY_GLAZED_TERRACOTTA);
                    entries.accept(Items.GRAY_GLAZED_TERRACOTTA);
                    entries.accept(Items.BLACK_GLAZED_TERRACOTTA);
                    entries.accept(Items.BROWN_GLAZED_TERRACOTTA);
                    entries.accept(Items.RED_GLAZED_TERRACOTTA);
                    entries.accept(Items.ORANGE_GLAZED_TERRACOTTA);
                    entries.accept(Items.YELLOW_GLAZED_TERRACOTTA);
                    entries.accept(Items.LIME_GLAZED_TERRACOTTA);
                    entries.accept(Items.GREEN_GLAZED_TERRACOTTA);
                    entries.accept(Items.CYAN_GLAZED_TERRACOTTA);
                    entries.accept(Items.LIGHT_BLUE_GLAZED_TERRACOTTA);
                    entries.accept(Items.BLUE_GLAZED_TERRACOTTA);
                    entries.accept(Items.PURPLE_GLAZED_TERRACOTTA);
                    entries.accept(Items.MAGENTA_GLAZED_TERRACOTTA);
                    entries.accept(Items.PINK_GLAZED_TERRACOTTA);
                    entries.accept(Items.GLASS);
                    entries.accept(Items.TINTED_GLASS);
                    entries.accept(Items.WHITE_STAINED_GLASS);
                    entries.accept(Items.LIGHT_GRAY_STAINED_GLASS);
                    entries.accept(Items.GRAY_STAINED_GLASS);
                    entries.accept(Items.BLACK_STAINED_GLASS);
                    entries.accept(Items.BROWN_STAINED_GLASS);
                    entries.accept(Items.RED_STAINED_GLASS);
                    entries.accept(Items.ORANGE_STAINED_GLASS);
                    entries.accept(Items.YELLOW_STAINED_GLASS);
                    entries.accept(Items.LIME_STAINED_GLASS);
                    entries.accept(Items.GREEN_STAINED_GLASS);
                    entries.accept(Items.CYAN_STAINED_GLASS);
                    entries.accept(Items.LIGHT_BLUE_STAINED_GLASS);
                    entries.accept(Items.BLUE_STAINED_GLASS);
                    entries.accept(Items.PURPLE_STAINED_GLASS);
                    entries.accept(Items.MAGENTA_STAINED_GLASS);
                    entries.accept(Items.PINK_STAINED_GLASS);
                    entries.accept(Items.GLASS_PANE);
                    entries.accept(Items.WHITE_STAINED_GLASS_PANE);
                    entries.accept(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
                    entries.accept(Items.GRAY_STAINED_GLASS_PANE);
                    entries.accept(Items.BLACK_STAINED_GLASS_PANE);
                    entries.accept(Items.BROWN_STAINED_GLASS_PANE);
                    entries.accept(Items.RED_STAINED_GLASS_PANE);
                    entries.accept(Items.ORANGE_STAINED_GLASS_PANE);
                    entries.accept(Items.YELLOW_STAINED_GLASS_PANE);
                    entries.accept(Items.LIME_STAINED_GLASS_PANE);
                    entries.accept(Items.GREEN_STAINED_GLASS_PANE);
                    entries.accept(Items.CYAN_STAINED_GLASS_PANE);
                    entries.accept(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
                    entries.accept(Items.BLUE_STAINED_GLASS_PANE);
                    entries.accept(Items.PURPLE_STAINED_GLASS_PANE);
                    entries.accept(Items.MAGENTA_STAINED_GLASS_PANE);
                    entries.accept(Items.PINK_STAINED_GLASS_PANE);
                    entries.accept(Items.SHULKER_BOX);
                    entries.accept(Items.WHITE_SHULKER_BOX);
                    entries.accept(Items.LIGHT_GRAY_SHULKER_BOX);
                    entries.accept(Items.GRAY_SHULKER_BOX);
                    entries.accept(Items.BLACK_SHULKER_BOX);
                    entries.accept(Items.BROWN_SHULKER_BOX);
                    entries.accept(Items.RED_SHULKER_BOX);
                    entries.accept(Items.ORANGE_SHULKER_BOX);
                    entries.accept(Items.YELLOW_SHULKER_BOX);
                    entries.accept(Items.LIME_SHULKER_BOX);
                    entries.accept(Items.GREEN_SHULKER_BOX);
                    entries.accept(Items.CYAN_SHULKER_BOX);
                    entries.accept(Items.LIGHT_BLUE_SHULKER_BOX);
                    entries.accept(Items.BLUE_SHULKER_BOX);
                    entries.accept(Items.PURPLE_SHULKER_BOX);
                    entries.accept(Items.MAGENTA_SHULKER_BOX);
                    entries.accept(Items.PINK_SHULKER_BOX);
                    entries.accept(Items.WHITE_BED);
                    entries.accept(Items.LIGHT_GRAY_BED);
                    entries.accept(Items.GRAY_BED);
                    entries.accept(Items.BLACK_BED);
                    entries.accept(Items.BROWN_BED);
                    entries.accept(Items.RED_BED);
                    entries.accept(Items.ORANGE_BED);
                    entries.accept(Items.YELLOW_BED);
                    entries.accept(Items.LIME_BED);
                    entries.accept(Items.GREEN_BED);
                    entries.accept(Items.CYAN_BED);
                    entries.accept(Items.LIGHT_BLUE_BED);
                    entries.accept(Items.BLUE_BED);
                    entries.accept(Items.PURPLE_BED);
                    entries.accept(Items.MAGENTA_BED);
                    entries.accept(Items.PINK_BED);
                    entries.accept(Items.CANDLE);
                    entries.accept(Items.WHITE_CANDLE);
                    entries.accept(Items.LIGHT_GRAY_CANDLE);
                    entries.accept(Items.GRAY_CANDLE);
                    entries.accept(Items.BLACK_CANDLE);
                    entries.accept(Items.BROWN_CANDLE);
                    entries.accept(Items.RED_CANDLE);
                    entries.accept(Items.ORANGE_CANDLE);
                    entries.accept(Items.YELLOW_CANDLE);
                    entries.accept(Items.LIME_CANDLE);
                    entries.accept(Items.GREEN_CANDLE);
                    entries.accept(Items.CYAN_CANDLE);
                    entries.accept(Items.LIGHT_BLUE_CANDLE);
                    entries.accept(Items.BLUE_CANDLE);
                    entries.accept(Items.PURPLE_CANDLE);
                    entries.accept(Items.MAGENTA_CANDLE);
                    entries.accept(Items.PINK_CANDLE);
                    entries.accept(Items.WHITE_BANNER);
                    entries.accept(Items.LIGHT_GRAY_BANNER);
                    entries.accept(Items.GRAY_BANNER);
                    entries.accept(Items.BLACK_BANNER);
                    entries.accept(Items.BROWN_BANNER);
                    entries.accept(Items.RED_BANNER);
                    entries.accept(Items.ORANGE_BANNER);
                    entries.accept(Items.YELLOW_BANNER);
                    entries.accept(Items.LIME_BANNER);
                    entries.accept(Items.GREEN_BANNER);
                    entries.accept(Items.CYAN_BANNER);
                    entries.accept(Items.LIGHT_BLUE_BANNER);
                    entries.accept(Items.BLUE_BANNER);
                    entries.accept(Items.PURPLE_BANNER);
                    entries.accept(Items.MAGENTA_BANNER);
                    entries.accept(Items.PINK_BANNER);
                })
                .build()
        );
        Registry.register(
            registry,
            NATURAL_BLOCKS,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 2)
                .title(Component.translatable("itemGroup.natural"))
                .icon(() -> new ItemStack(Blocks.GRASS_BLOCK))
                .displayItems((displayContext, entries) -> {
                    entries.accept(Items.GRASS_BLOCK);
                    entries.accept(Items.PODZOL);
                    entries.accept(Items.MYCELIUM);
                    entries.accept(Items.DIRT_PATH);
                    entries.accept(Items.DIRT);
                    entries.accept(Items.COARSE_DIRT);
                    entries.accept(Items.ROOTED_DIRT);
                    entries.accept(Items.FARMLAND);
                    entries.accept(Items.MUD);
                    entries.accept(Items.CLAY);
                    entries.accept(Items.GRAVEL);
                    entries.accept(Items.SAND);
                    entries.accept(Items.SANDSTONE);
                    entries.accept(Items.RED_SAND);
                    entries.accept(Items.RED_SANDSTONE);
                    entries.accept(Items.ICE);
                    entries.accept(Items.PACKED_ICE);
                    entries.accept(Items.BLUE_ICE);
                    entries.accept(Items.SNOW_BLOCK);
                    entries.accept(Items.SNOW);
                    entries.accept(Items.MOSS_BLOCK);
                    entries.accept(Items.MOSS_CARPET);
                    entries.accept(Items.STONE);
                    entries.accept(Items.DEEPSLATE);
                    entries.accept(Items.GRANITE);
                    entries.accept(Items.DIORITE);
                    entries.accept(Items.ANDESITE);
                    entries.accept(Items.CALCITE);
                    entries.accept(Items.TUFF);
                    entries.accept(Items.DRIPSTONE_BLOCK);
                    entries.accept(Items.POINTED_DRIPSTONE);
                    entries.accept(Items.PRISMARINE);
                    entries.accept(Items.MAGMA_BLOCK);
                    entries.accept(Items.OBSIDIAN);
                    entries.accept(Items.CRYING_OBSIDIAN);
                    entries.accept(Items.NETHERRACK);
                    entries.accept(Items.CRIMSON_NYLIUM);
                    entries.accept(Items.WARPED_NYLIUM);
                    entries.accept(Items.SOUL_SAND);
                    entries.accept(Items.SOUL_SOIL);
                    entries.accept(Items.BONE_BLOCK);
                    entries.accept(Items.BLACKSTONE);
                    entries.accept(Items.BASALT);
                    entries.accept(Items.SMOOTH_BASALT);
                    entries.accept(Items.END_STONE);
                    entries.accept(Items.COAL_ORE);
                    entries.accept(Items.DEEPSLATE_COAL_ORE);
                    entries.accept(Items.IRON_ORE);
                    entries.accept(Items.DEEPSLATE_IRON_ORE);
                    entries.accept(Items.COPPER_ORE);
                    entries.accept(Items.DEEPSLATE_COPPER_ORE);
                    entries.accept(Items.GOLD_ORE);
                    entries.accept(Items.DEEPSLATE_GOLD_ORE);
                    entries.accept(Items.REDSTONE_ORE);
                    entries.accept(Items.DEEPSLATE_REDSTONE_ORE);
                    entries.accept(Items.EMERALD_ORE);
                    entries.accept(Items.DEEPSLATE_EMERALD_ORE);
                    entries.accept(Items.LAPIS_ORE);
                    entries.accept(Items.DEEPSLATE_LAPIS_ORE);
                    entries.accept(Items.DIAMOND_ORE);
                    entries.accept(Items.DEEPSLATE_DIAMOND_ORE);
                    entries.accept(Items.NETHER_GOLD_ORE);
                    entries.accept(Items.NETHER_QUARTZ_ORE);
                    entries.accept(Items.ANCIENT_DEBRIS);
                    entries.accept(Items.RAW_IRON_BLOCK);
                    entries.accept(Items.RAW_COPPER_BLOCK);
                    entries.accept(Items.RAW_GOLD_BLOCK);
                    entries.accept(Items.GLOWSTONE);
                    entries.accept(Items.AMETHYST_BLOCK);
                    entries.accept(Items.BUDDING_AMETHYST);
                    entries.accept(Items.SMALL_AMETHYST_BUD);
                    entries.accept(Items.MEDIUM_AMETHYST_BUD);
                    entries.accept(Items.LARGE_AMETHYST_BUD);
                    entries.accept(Items.AMETHYST_CLUSTER);
                    entries.accept(Items.OAK_LOG);
                    entries.accept(Items.SPRUCE_LOG);
                    entries.accept(Items.BIRCH_LOG);
                    entries.accept(Items.JUNGLE_LOG);
                    entries.accept(Items.ACACIA_LOG);
                    entries.accept(Items.DARK_OAK_LOG);
                    entries.accept(Items.MANGROVE_LOG);
                    entries.accept(Items.MANGROVE_ROOTS);
                    entries.accept(Items.MUDDY_MANGROVE_ROOTS);
                    entries.accept(Items.CHERRY_LOG);
                    entries.accept(Items.MUSHROOM_STEM);
                    entries.accept(Items.CRIMSON_STEM);
                    entries.accept(Items.WARPED_STEM);
                    entries.accept(Items.OAK_LEAVES);
                    entries.accept(Items.SPRUCE_LEAVES);
                    entries.accept(Items.BIRCH_LEAVES);
                    entries.accept(Items.JUNGLE_LEAVES);
                    entries.accept(Items.ACACIA_LEAVES);
                    entries.accept(Items.DARK_OAK_LEAVES);
                    entries.accept(Items.MANGROVE_LEAVES);
                    entries.accept(Items.CHERRY_LEAVES);
                    entries.accept(Items.AZALEA_LEAVES);
                    entries.accept(Items.FLOWERING_AZALEA_LEAVES);
                    entries.accept(Items.BROWN_MUSHROOM_BLOCK);
                    entries.accept(Items.RED_MUSHROOM_BLOCK);
                    entries.accept(Items.NETHER_WART_BLOCK);
                    entries.accept(Items.WARPED_WART_BLOCK);
                    entries.accept(Items.SHROOMLIGHT);
                    entries.accept(Items.OAK_SAPLING);
                    entries.accept(Items.SPRUCE_SAPLING);
                    entries.accept(Items.BIRCH_SAPLING);
                    entries.accept(Items.JUNGLE_SAPLING);
                    entries.accept(Items.ACACIA_SAPLING);
                    entries.accept(Items.DARK_OAK_SAPLING);
                    entries.accept(Items.MANGROVE_PROPAGULE);
                    entries.accept(Items.CHERRY_SAPLING);
                    entries.accept(Items.AZALEA);
                    entries.accept(Items.FLOWERING_AZALEA);
                    entries.accept(Items.BROWN_MUSHROOM);
                    entries.accept(Items.RED_MUSHROOM);
                    entries.accept(Items.CRIMSON_FUNGUS);
                    entries.accept(Items.WARPED_FUNGUS);
                    entries.accept(Items.SHORT_GRASS);
                    entries.accept(Items.FERN);
                    entries.accept(Items.DEAD_BUSH);
                    entries.accept(Items.DANDELION);
                    entries.accept(Items.POPPY);
                    entries.accept(Items.BLUE_ORCHID);
                    entries.accept(Items.ALLIUM);
                    entries.accept(Items.AZURE_BLUET);
                    entries.accept(Items.RED_TULIP);
                    entries.accept(Items.ORANGE_TULIP);
                    entries.accept(Items.WHITE_TULIP);
                    entries.accept(Items.PINK_TULIP);
                    entries.accept(Items.OXEYE_DAISY);
                    entries.accept(Items.CORNFLOWER);
                    entries.accept(Items.LILY_OF_THE_VALLEY);
                    entries.accept(Items.TORCHFLOWER);
                    entries.accept(Items.WITHER_ROSE);
                    entries.accept(Items.PINK_PETALS);
                    entries.accept(Items.SPORE_BLOSSOM);
                    entries.accept(Items.BAMBOO);
                    entries.accept(Items.SUGAR_CANE);
                    entries.accept(Items.CACTUS);
                    entries.accept(Items.CRIMSON_ROOTS);
                    entries.accept(Items.WARPED_ROOTS);
                    entries.accept(Items.NETHER_SPROUTS);
                    entries.accept(Items.WEEPING_VINES);
                    entries.accept(Items.TWISTING_VINES);
                    entries.accept(Items.VINE);
                    entries.accept(Items.TALL_GRASS);
                    entries.accept(Items.LARGE_FERN);
                    entries.accept(Items.SUNFLOWER);
                    entries.accept(Items.LILAC);
                    entries.accept(Items.ROSE_BUSH);
                    entries.accept(Items.PEONY);
                    entries.accept(Items.PITCHER_PLANT);
                    entries.accept(Items.BIG_DRIPLEAF);
                    entries.accept(Items.SMALL_DRIPLEAF);
                    entries.accept(Items.CHORUS_PLANT);
                    entries.accept(Items.CHORUS_FLOWER);
                    entries.accept(Items.GLOW_LICHEN);
                    entries.accept(Items.HANGING_ROOTS);
                    entries.accept(Items.FROGSPAWN);
                    entries.accept(Items.TURTLE_EGG);
                    entries.accept(Items.SNIFFER_EGG);
                    entries.accept(Items.WHEAT_SEEDS);
                    entries.accept(Items.COCOA_BEANS);
                    entries.accept(Items.PUMPKIN_SEEDS);
                    entries.accept(Items.MELON_SEEDS);
                    entries.accept(Items.BEETROOT_SEEDS);
                    entries.accept(Items.TORCHFLOWER_SEEDS);
                    entries.accept(Items.PITCHER_POD);
                    entries.accept(Items.GLOW_BERRIES);
                    entries.accept(Items.SWEET_BERRIES);
                    entries.accept(Items.NETHER_WART);
                    entries.accept(Items.LILY_PAD);
                    entries.accept(Items.SEAGRASS);
                    entries.accept(Items.SEA_PICKLE);
                    entries.accept(Items.KELP);
                    entries.accept(Items.DRIED_KELP_BLOCK);
                    entries.accept(Items.TUBE_CORAL_BLOCK);
                    entries.accept(Items.BRAIN_CORAL_BLOCK);
                    entries.accept(Items.BUBBLE_CORAL_BLOCK);
                    entries.accept(Items.FIRE_CORAL_BLOCK);
                    entries.accept(Items.HORN_CORAL_BLOCK);
                    entries.accept(Items.DEAD_TUBE_CORAL_BLOCK);
                    entries.accept(Items.DEAD_BRAIN_CORAL_BLOCK);
                    entries.accept(Items.DEAD_BUBBLE_CORAL_BLOCK);
                    entries.accept(Items.DEAD_FIRE_CORAL_BLOCK);
                    entries.accept(Items.DEAD_HORN_CORAL_BLOCK);
                    entries.accept(Items.TUBE_CORAL);
                    entries.accept(Items.BRAIN_CORAL);
                    entries.accept(Items.BUBBLE_CORAL);
                    entries.accept(Items.FIRE_CORAL);
                    entries.accept(Items.HORN_CORAL);
                    entries.accept(Items.DEAD_TUBE_CORAL);
                    entries.accept(Items.DEAD_BRAIN_CORAL);
                    entries.accept(Items.DEAD_BUBBLE_CORAL);
                    entries.accept(Items.DEAD_FIRE_CORAL);
                    entries.accept(Items.DEAD_HORN_CORAL);
                    entries.accept(Items.TUBE_CORAL_FAN);
                    entries.accept(Items.BRAIN_CORAL_FAN);
                    entries.accept(Items.BUBBLE_CORAL_FAN);
                    entries.accept(Items.FIRE_CORAL_FAN);
                    entries.accept(Items.HORN_CORAL_FAN);
                    entries.accept(Items.DEAD_TUBE_CORAL_FAN);
                    entries.accept(Items.DEAD_BRAIN_CORAL_FAN);
                    entries.accept(Items.DEAD_BUBBLE_CORAL_FAN);
                    entries.accept(Items.DEAD_FIRE_CORAL_FAN);
                    entries.accept(Items.DEAD_HORN_CORAL_FAN);
                    entries.accept(Items.SPONGE);
                    entries.accept(Items.WET_SPONGE);
                    entries.accept(Items.MELON);
                    entries.accept(Items.PUMPKIN);
                    entries.accept(Items.CARVED_PUMPKIN);
                    entries.accept(Items.JACK_O_LANTERN);
                    entries.accept(Items.HAY_BLOCK);
                    entries.accept(Items.BEE_NEST);
                    entries.accept(Items.HONEYCOMB_BLOCK);
                    entries.accept(Items.SLIME_BLOCK);
                    entries.accept(Items.HONEY_BLOCK);
                    entries.accept(Items.OCHRE_FROGLIGHT);
                    entries.accept(Items.VERDANT_FROGLIGHT);
                    entries.accept(Items.PEARLESCENT_FROGLIGHT);
                    entries.accept(Items.SCULK);
                    entries.accept(Items.SCULK_VEIN);
                    entries.accept(Items.SCULK_CATALYST);
                    entries.accept(Items.SCULK_SHRIEKER);
                    entries.accept(Items.SCULK_SENSOR);
                    entries.accept(Items.COBWEB);
                    entries.accept(Items.BEDROCK);
                })
                .build()
        );
        Registry.register(
            registry,
            FUNCTIONAL_BLOCKS,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 3)
                .title(Component.translatable("itemGroup.functional"))
                .icon(() -> new ItemStack(Items.OAK_SIGN))
                .displayItems(
                    (displayContext, entries) -> {
                        entries.accept(Items.TORCH);
                        entries.accept(Items.SOUL_TORCH);
                        entries.accept(Items.REDSTONE_TORCH);
                        entries.accept(Items.LANTERN);
                        entries.accept(Items.SOUL_LANTERN);
                        entries.accept(Items.CHAIN);
                        entries.accept(Items.END_ROD);
                        entries.accept(Items.SEA_LANTERN);
                        entries.accept(Items.REDSTONE_LAMP);
                        entries.accept(Items.COPPER_BULB);
                        entries.accept(Items.EXPOSED_COPPER_BULB);
                        entries.accept(Items.WEATHERED_COPPER_BULB);
                        entries.accept(Items.OXIDIZED_COPPER_BULB);
                        entries.accept(Items.WAXED_COPPER_BULB);
                        entries.accept(Items.WAXED_EXPOSED_COPPER_BULB);
                        entries.accept(Items.WAXED_WEATHERED_COPPER_BULB);
                        entries.accept(Items.WAXED_OXIDIZED_COPPER_BULB);
                        entries.accept(Items.GLOWSTONE);
                        entries.accept(Items.SHROOMLIGHT);
                        entries.accept(Items.OCHRE_FROGLIGHT);
                        entries.accept(Items.VERDANT_FROGLIGHT);
                        entries.accept(Items.PEARLESCENT_FROGLIGHT);
                        entries.accept(Items.CRYING_OBSIDIAN);
                        entries.accept(Items.GLOW_LICHEN);
                        entries.accept(Items.MAGMA_BLOCK);
                        entries.accept(Items.CRAFTING_TABLE);
                        entries.accept(Items.STONECUTTER);
                        entries.accept(Items.CARTOGRAPHY_TABLE);
                        entries.accept(Items.FLETCHING_TABLE);
                        entries.accept(Items.SMITHING_TABLE);
                        entries.accept(Items.GRINDSTONE);
                        entries.accept(Items.LOOM);
                        entries.accept(Items.FURNACE);
                        entries.accept(Items.SMOKER);
                        entries.accept(Items.BLAST_FURNACE);
                        entries.accept(Items.CAMPFIRE);
                        entries.accept(Items.SOUL_CAMPFIRE);
                        entries.accept(Items.ANVIL);
                        entries.accept(Items.CHIPPED_ANVIL);
                        entries.accept(Items.DAMAGED_ANVIL);
                        entries.accept(Items.COMPOSTER);
                        entries.accept(Items.NOTE_BLOCK);
                        entries.accept(Items.JUKEBOX);
                        entries.accept(Items.ENCHANTING_TABLE);
                        entries.accept(Items.END_CRYSTAL);
                        entries.accept(Items.BREWING_STAND);
                        entries.accept(Items.CAULDRON);
                        entries.accept(Items.BELL);
                        entries.accept(Items.BEACON);
                        entries.accept(Items.CONDUIT);
                        entries.accept(Items.LODESTONE);
                        entries.accept(Items.LADDER);
                        entries.accept(Items.SCAFFOLDING);
                        entries.accept(Items.BEE_NEST);
                        entries.accept(Items.BEEHIVE);
                        entries.accept(Items.SUSPICIOUS_SAND);
                        entries.accept(Items.SUSPICIOUS_GRAVEL);
                        entries.accept(Items.LIGHTNING_ROD);
                        entries.accept(Items.FLOWER_POT);
                        entries.accept(Items.DECORATED_POT);
                        entries.accept(Items.ARMOR_STAND);
                        entries.accept(Items.ITEM_FRAME);
                        entries.accept(Items.GLOW_ITEM_FRAME);
                        entries.accept(Items.PAINTING);
                        displayContext.holders()
                            .lookup(Registries.PAINTING_VARIANT)
                            .ifPresent(
                                registryWrapper -> generatePresetPaintings(
                                        entries,
                                        displayContext.holders(),
                                        (HolderLookup.RegistryLookup<PaintingVariant>)registryWrapper,
                                        registryEntry -> registryEntry.is(PaintingVariantTags.PLACEABLE),
                                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                    )
                            );
                        entries.accept(Items.BOOKSHELF);
                        entries.accept(Items.CHISELED_BOOKSHELF);
                        entries.accept(Items.LECTERN);
                        entries.accept(Items.TINTED_GLASS);
                        entries.accept(Items.OAK_SIGN);
                        entries.accept(Items.OAK_HANGING_SIGN);
                        entries.accept(Items.SPRUCE_SIGN);
                        entries.accept(Items.SPRUCE_HANGING_SIGN);
                        entries.accept(Items.BIRCH_SIGN);
                        entries.accept(Items.BIRCH_HANGING_SIGN);
                        entries.accept(Items.JUNGLE_SIGN);
                        entries.accept(Items.JUNGLE_HANGING_SIGN);
                        entries.accept(Items.ACACIA_SIGN);
                        entries.accept(Items.ACACIA_HANGING_SIGN);
                        entries.accept(Items.DARK_OAK_SIGN);
                        entries.accept(Items.DARK_OAK_HANGING_SIGN);
                        entries.accept(Items.MANGROVE_SIGN);
                        entries.accept(Items.MANGROVE_HANGING_SIGN);
                        entries.accept(Items.CHERRY_SIGN);
                        entries.accept(Items.CHERRY_HANGING_SIGN);
                        entries.accept(Items.BAMBOO_SIGN);
                        entries.accept(Items.BAMBOO_HANGING_SIGN);
                        entries.accept(Items.CRIMSON_SIGN);
                        entries.accept(Items.CRIMSON_HANGING_SIGN);
                        entries.accept(Items.WARPED_SIGN);
                        entries.accept(Items.WARPED_HANGING_SIGN);
                        entries.accept(Items.CHEST);
                        entries.accept(Items.BARREL);
                        entries.accept(Items.ENDER_CHEST);
                        entries.accept(Items.SHULKER_BOX);
                        entries.accept(Items.WHITE_SHULKER_BOX);
                        entries.accept(Items.LIGHT_GRAY_SHULKER_BOX);
                        entries.accept(Items.GRAY_SHULKER_BOX);
                        entries.accept(Items.BLACK_SHULKER_BOX);
                        entries.accept(Items.BROWN_SHULKER_BOX);
                        entries.accept(Items.RED_SHULKER_BOX);
                        entries.accept(Items.ORANGE_SHULKER_BOX);
                        entries.accept(Items.YELLOW_SHULKER_BOX);
                        entries.accept(Items.LIME_SHULKER_BOX);
                        entries.accept(Items.GREEN_SHULKER_BOX);
                        entries.accept(Items.CYAN_SHULKER_BOX);
                        entries.accept(Items.LIGHT_BLUE_SHULKER_BOX);
                        entries.accept(Items.BLUE_SHULKER_BOX);
                        entries.accept(Items.PURPLE_SHULKER_BOX);
                        entries.accept(Items.MAGENTA_SHULKER_BOX);
                        entries.accept(Items.PINK_SHULKER_BOX);
                        entries.accept(Items.RESPAWN_ANCHOR);
                        entries.accept(Items.WHITE_BED);
                        entries.accept(Items.LIGHT_GRAY_BED);
                        entries.accept(Items.GRAY_BED);
                        entries.accept(Items.BLACK_BED);
                        entries.accept(Items.BROWN_BED);
                        entries.accept(Items.RED_BED);
                        entries.accept(Items.ORANGE_BED);
                        entries.accept(Items.YELLOW_BED);
                        entries.accept(Items.LIME_BED);
                        entries.accept(Items.GREEN_BED);
                        entries.accept(Items.CYAN_BED);
                        entries.accept(Items.LIGHT_BLUE_BED);
                        entries.accept(Items.BLUE_BED);
                        entries.accept(Items.PURPLE_BED);
                        entries.accept(Items.MAGENTA_BED);
                        entries.accept(Items.PINK_BED);
                        entries.accept(Items.CANDLE);
                        entries.accept(Items.WHITE_CANDLE);
                        entries.accept(Items.LIGHT_GRAY_CANDLE);
                        entries.accept(Items.GRAY_CANDLE);
                        entries.accept(Items.BLACK_CANDLE);
                        entries.accept(Items.BROWN_CANDLE);
                        entries.accept(Items.RED_CANDLE);
                        entries.accept(Items.ORANGE_CANDLE);
                        entries.accept(Items.YELLOW_CANDLE);
                        entries.accept(Items.LIME_CANDLE);
                        entries.accept(Items.GREEN_CANDLE);
                        entries.accept(Items.CYAN_CANDLE);
                        entries.accept(Items.LIGHT_BLUE_CANDLE);
                        entries.accept(Items.BLUE_CANDLE);
                        entries.accept(Items.PURPLE_CANDLE);
                        entries.accept(Items.MAGENTA_CANDLE);
                        entries.accept(Items.PINK_CANDLE);
                        entries.accept(Items.WHITE_BANNER);
                        entries.accept(Items.LIGHT_GRAY_BANNER);
                        entries.accept(Items.GRAY_BANNER);
                        entries.accept(Items.BLACK_BANNER);
                        entries.accept(Items.BROWN_BANNER);
                        entries.accept(Items.RED_BANNER);
                        entries.accept(Items.ORANGE_BANNER);
                        entries.accept(Items.YELLOW_BANNER);
                        entries.accept(Items.LIME_BANNER);
                        entries.accept(Items.GREEN_BANNER);
                        entries.accept(Items.CYAN_BANNER);
                        entries.accept(Items.LIGHT_BLUE_BANNER);
                        entries.accept(Items.BLUE_BANNER);
                        entries.accept(Items.PURPLE_BANNER);
                        entries.accept(Items.MAGENTA_BANNER);
                        entries.accept(Items.PINK_BANNER);
                        entries.accept(Raid.getLeaderBannerInstance(displayContext.holders().lookupOrThrow(Registries.BANNER_PATTERN)));
                        entries.accept(Items.SKELETON_SKULL);
                        entries.accept(Items.WITHER_SKELETON_SKULL);
                        entries.accept(Items.PLAYER_HEAD);
                        entries.accept(Items.ZOMBIE_HEAD);
                        entries.accept(Items.CREEPER_HEAD);
                        entries.accept(Items.PIGLIN_HEAD);
                        entries.accept(Items.DRAGON_HEAD);
                        entries.accept(Items.DRAGON_EGG);
                        entries.accept(Items.END_PORTAL_FRAME);
                        entries.accept(Items.ENDER_EYE);
                        entries.accept(Items.VAULT);
                        entries.accept(Items.INFESTED_STONE);
                        entries.accept(Items.INFESTED_COBBLESTONE);
                        entries.accept(Items.INFESTED_STONE_BRICKS);
                        entries.accept(Items.INFESTED_MOSSY_STONE_BRICKS);
                        entries.accept(Items.INFESTED_CRACKED_STONE_BRICKS);
                        entries.accept(Items.INFESTED_CHISELED_STONE_BRICKS);
                        entries.accept(Items.INFESTED_DEEPSLATE);
                    }
                )
                .build()
        );
        Registry.register(
            registry,
            REDSTONE_BLOCKS,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 4)
                .title(Component.translatable("itemGroup.redstone"))
                .icon(() -> new ItemStack(Items.REDSTONE))
                .displayItems((displayContext, entries) -> {
                    entries.accept(Items.REDSTONE);
                    entries.accept(Items.REDSTONE_TORCH);
                    entries.accept(Items.REDSTONE_BLOCK);
                    entries.accept(Items.REPEATER);
                    entries.accept(Items.COMPARATOR);
                    entries.accept(Items.TARGET);
                    entries.accept(Items.WAXED_COPPER_BULB);
                    entries.accept(Items.WAXED_EXPOSED_COPPER_BULB);
                    entries.accept(Items.WAXED_WEATHERED_COPPER_BULB);
                    entries.accept(Items.WAXED_OXIDIZED_COPPER_BULB);
                    entries.accept(Items.LEVER);
                    entries.accept(Items.OAK_BUTTON);
                    entries.accept(Items.STONE_BUTTON);
                    entries.accept(Items.OAK_PRESSURE_PLATE);
                    entries.accept(Items.STONE_PRESSURE_PLATE);
                    entries.accept(Items.LIGHT_WEIGHTED_PRESSURE_PLATE);
                    entries.accept(Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
                    entries.accept(Items.SCULK_SENSOR);
                    entries.accept(Items.CALIBRATED_SCULK_SENSOR);
                    entries.accept(Items.SCULK_SHRIEKER);
                    entries.accept(Items.AMETHYST_BLOCK);
                    entries.accept(Items.WHITE_WOOL);
                    entries.accept(Items.TRIPWIRE_HOOK);
                    entries.accept(Items.STRING);
                    entries.accept(Items.LECTERN);
                    entries.accept(Items.DAYLIGHT_DETECTOR);
                    entries.accept(Items.LIGHTNING_ROD);
                    entries.accept(Items.PISTON);
                    entries.accept(Items.STICKY_PISTON);
                    entries.accept(Items.SLIME_BLOCK);
                    entries.accept(Items.HONEY_BLOCK);
                    entries.accept(Items.DISPENSER);
                    entries.accept(Items.DROPPER);
                    entries.accept(Items.CRAFTER);
                    entries.accept(Items.HOPPER);
                    entries.accept(Items.CHEST);
                    entries.accept(Items.BARREL);
                    entries.accept(Items.CHISELED_BOOKSHELF);
                    entries.accept(Items.FURNACE);
                    entries.accept(Items.TRAPPED_CHEST);
                    entries.accept(Items.JUKEBOX);
                    entries.accept(Items.DECORATED_POT);
                    entries.accept(Items.OBSERVER);
                    entries.accept(Items.NOTE_BLOCK);
                    entries.accept(Items.COMPOSTER);
                    entries.accept(Items.CAULDRON);
                    entries.accept(Items.RAIL);
                    entries.accept(Items.POWERED_RAIL);
                    entries.accept(Items.DETECTOR_RAIL);
                    entries.accept(Items.ACTIVATOR_RAIL);
                    entries.accept(Items.MINECART);
                    entries.accept(Items.HOPPER_MINECART);
                    entries.accept(Items.CHEST_MINECART);
                    entries.accept(Items.FURNACE_MINECART);
                    entries.accept(Items.TNT_MINECART);
                    entries.accept(Items.OAK_CHEST_BOAT);
                    entries.accept(Items.BAMBOO_CHEST_RAFT);
                    entries.accept(Items.OAK_DOOR);
                    entries.accept(Items.IRON_DOOR);
                    entries.accept(Items.OAK_FENCE_GATE);
                    entries.accept(Items.OAK_TRAPDOOR);
                    entries.accept(Items.IRON_TRAPDOOR);
                    entries.accept(Items.TNT);
                    entries.accept(Items.REDSTONE_LAMP);
                    entries.accept(Items.BELL);
                    entries.accept(Items.BIG_DRIPLEAF);
                    entries.accept(Items.ARMOR_STAND);
                    entries.accept(Items.REDSTONE_ORE);
                })
                .build()
        );
        Registry.register(
            registry,
            HOTBAR,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 5)
                .title(Component.translatable("itemGroup.hotbar"))
                .icon(() -> new ItemStack(Blocks.BOOKSHELF))
                .alignedRight()
                .type(CreativeModeTab.Type.HOTBAR)
                .build()
        );
        Registry.register(
            registry,
            SEARCH,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 6)
                .title(Component.translatable("itemGroup.search"))
                .icon(() -> new ItemStack(Items.COMPASS))
                .displayItems((displayContext, entries) -> {
                    Set<ItemStack> set = ItemStackLinkedSet.createTypeAndComponentsSet();

                    for (CreativeModeTab creativeModeTab : registry) {
                        if (creativeModeTab.getType() != CreativeModeTab.Type.SEARCH) {
                            set.addAll(creativeModeTab.getSearchTabDisplayItems());
                        }
                    }

                    entries.acceptAll(set);
                })
                .backgroundTexture(SEARCH_BACKGROUND)
                .alignedRight()
                .type(CreativeModeTab.Type.SEARCH)
                .build()
        );
        Registry.register(
            registry,
            TOOLS_AND_UTILITIES,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
                .title(Component.translatable("itemGroup.tools"))
                .icon(() -> new ItemStack(Items.DIAMOND_PICKAXE))
                .displayItems(
                    (displayContext, entries) -> {
                        entries.accept(Items.WOODEN_SHOVEL);
                        entries.accept(Items.WOODEN_PICKAXE);
                        entries.accept(Items.WOODEN_AXE);
                        entries.accept(Items.WOODEN_HOE);
                        entries.accept(Items.STONE_SHOVEL);
                        entries.accept(Items.STONE_PICKAXE);
                        entries.accept(Items.STONE_AXE);
                        entries.accept(Items.STONE_HOE);
                        entries.accept(Items.IRON_SHOVEL);
                        entries.accept(Items.IRON_PICKAXE);
                        entries.accept(Items.IRON_AXE);
                        entries.accept(Items.IRON_HOE);
                        entries.accept(Items.GOLDEN_SHOVEL);
                        entries.accept(Items.GOLDEN_PICKAXE);
                        entries.accept(Items.GOLDEN_AXE);
                        entries.accept(Items.GOLDEN_HOE);
                        entries.accept(Items.DIAMOND_SHOVEL);
                        entries.accept(Items.DIAMOND_PICKAXE);
                        entries.accept(Items.DIAMOND_AXE);
                        entries.accept(Items.DIAMOND_HOE);
                        entries.accept(Items.NETHERITE_SHOVEL);
                        entries.accept(Items.NETHERITE_PICKAXE);
                        entries.accept(Items.NETHERITE_AXE);
                        entries.accept(Items.NETHERITE_HOE);
                        entries.accept(Items.BUCKET);
                        entries.accept(Items.WATER_BUCKET);
                        entries.accept(Items.COD_BUCKET);
                        entries.accept(Items.SALMON_BUCKET);
                        entries.accept(Items.TROPICAL_FISH_BUCKET);
                        entries.accept(Items.PUFFERFISH_BUCKET);
                        entries.accept(Items.AXOLOTL_BUCKET);
                        entries.accept(Items.TADPOLE_BUCKET);
                        entries.accept(Items.LAVA_BUCKET);
                        entries.accept(Items.POWDER_SNOW_BUCKET);
                        entries.accept(Items.MILK_BUCKET);
                        entries.accept(Items.FISHING_ROD);
                        entries.accept(Items.FLINT_AND_STEEL);
                        entries.accept(Items.FIRE_CHARGE);
                        entries.accept(Items.BONE_MEAL);
                        entries.accept(Items.SHEARS);
                        entries.accept(Items.BRUSH);
                        entries.accept(Items.NAME_TAG);
                        entries.accept(Items.LEAD);
                        if (displayContext.enabledFeatures().contains(FeatureFlags.BUNDLE)) {
                            entries.accept(Items.BUNDLE);
                        }

                        entries.accept(Items.COMPASS);
                        entries.accept(Items.RECOVERY_COMPASS);
                        entries.accept(Items.CLOCK);
                        entries.accept(Items.SPYGLASS);
                        entries.accept(Items.MAP);
                        entries.accept(Items.WRITABLE_BOOK);
                        entries.accept(Items.WIND_CHARGE);
                        entries.accept(Items.ENDER_PEARL);
                        entries.accept(Items.ENDER_EYE);
                        entries.accept(Items.ELYTRA);
                        generateFireworksAllDurations(entries, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                        entries.accept(Items.SADDLE);
                        entries.accept(Items.CARROT_ON_A_STICK);
                        entries.accept(Items.WARPED_FUNGUS_ON_A_STICK);
                        entries.accept(Items.OAK_BOAT);
                        entries.accept(Items.OAK_CHEST_BOAT);
                        entries.accept(Items.SPRUCE_BOAT);
                        entries.accept(Items.SPRUCE_CHEST_BOAT);
                        entries.accept(Items.BIRCH_BOAT);
                        entries.accept(Items.BIRCH_CHEST_BOAT);
                        entries.accept(Items.JUNGLE_BOAT);
                        entries.accept(Items.JUNGLE_CHEST_BOAT);
                        entries.accept(Items.ACACIA_BOAT);
                        entries.accept(Items.ACACIA_CHEST_BOAT);
                        entries.accept(Items.DARK_OAK_BOAT);
                        entries.accept(Items.DARK_OAK_CHEST_BOAT);
                        entries.accept(Items.MANGROVE_BOAT);
                        entries.accept(Items.MANGROVE_CHEST_BOAT);
                        entries.accept(Items.CHERRY_BOAT);
                        entries.accept(Items.CHERRY_CHEST_BOAT);
                        entries.accept(Items.BAMBOO_RAFT);
                        entries.accept(Items.BAMBOO_CHEST_RAFT);
                        entries.accept(Items.RAIL);
                        entries.accept(Items.POWERED_RAIL);
                        entries.accept(Items.DETECTOR_RAIL);
                        entries.accept(Items.ACTIVATOR_RAIL);
                        entries.accept(Items.MINECART);
                        entries.accept(Items.HOPPER_MINECART);
                        entries.accept(Items.CHEST_MINECART);
                        entries.accept(Items.FURNACE_MINECART);
                        entries.accept(Items.TNT_MINECART);
                        displayContext.holders()
                            .lookup(Registries.INSTRUMENT)
                            .ifPresent(
                                wrapper -> generateInstrumentTypes(
                                        entries, wrapper, Items.GOAT_HORN, InstrumentTags.GOAT_HORNS, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                    )
                            );
                        entries.accept(Items.MUSIC_DISC_13);
                        entries.accept(Items.MUSIC_DISC_CAT);
                        entries.accept(Items.MUSIC_DISC_BLOCKS);
                        entries.accept(Items.MUSIC_DISC_CHIRP);
                        entries.accept(Items.MUSIC_DISC_FAR);
                        entries.accept(Items.MUSIC_DISC_MALL);
                        entries.accept(Items.MUSIC_DISC_MELLOHI);
                        entries.accept(Items.MUSIC_DISC_STAL);
                        entries.accept(Items.MUSIC_DISC_STRAD);
                        entries.accept(Items.MUSIC_DISC_WARD);
                        entries.accept(Items.MUSIC_DISC_11);
                        entries.accept(Items.MUSIC_DISC_CREATOR_MUSIC_BOX);
                        entries.accept(Items.MUSIC_DISC_WAIT);
                        entries.accept(Items.MUSIC_DISC_CREATOR);
                        entries.accept(Items.MUSIC_DISC_PRECIPICE);
                        entries.accept(Items.MUSIC_DISC_OTHERSIDE);
                        entries.accept(Items.MUSIC_DISC_RELIC);
                        entries.accept(Items.MUSIC_DISC_5);
                        entries.accept(Items.MUSIC_DISC_PIGSTEP);
                    }
                )
                .build()
        );
        Registry.register(
            registry,
            COMBAT,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 1)
                .title(Component.translatable("itemGroup.combat"))
                .icon(() -> new ItemStack(Items.NETHERITE_SWORD))
                .displayItems(
                    (displayContext, entries) -> {
                        entries.accept(Items.WOODEN_SWORD);
                        entries.accept(Items.STONE_SWORD);
                        entries.accept(Items.IRON_SWORD);
                        entries.accept(Items.GOLDEN_SWORD);
                        entries.accept(Items.DIAMOND_SWORD);
                        entries.accept(Items.NETHERITE_SWORD);
                        entries.accept(Items.WOODEN_AXE);
                        entries.accept(Items.STONE_AXE);
                        entries.accept(Items.IRON_AXE);
                        entries.accept(Items.GOLDEN_AXE);
                        entries.accept(Items.DIAMOND_AXE);
                        entries.accept(Items.NETHERITE_AXE);
                        entries.accept(Items.TRIDENT);
                        entries.accept(Items.MACE);
                        entries.accept(Items.SHIELD);
                        entries.accept(Items.LEATHER_HELMET);
                        entries.accept(Items.LEATHER_CHESTPLATE);
                        entries.accept(Items.LEATHER_LEGGINGS);
                        entries.accept(Items.LEATHER_BOOTS);
                        entries.accept(Items.CHAINMAIL_HELMET);
                        entries.accept(Items.CHAINMAIL_CHESTPLATE);
                        entries.accept(Items.CHAINMAIL_LEGGINGS);
                        entries.accept(Items.CHAINMAIL_BOOTS);
                        entries.accept(Items.IRON_HELMET);
                        entries.accept(Items.IRON_CHESTPLATE);
                        entries.accept(Items.IRON_LEGGINGS);
                        entries.accept(Items.IRON_BOOTS);
                        entries.accept(Items.GOLDEN_HELMET);
                        entries.accept(Items.GOLDEN_CHESTPLATE);
                        entries.accept(Items.GOLDEN_LEGGINGS);
                        entries.accept(Items.GOLDEN_BOOTS);
                        entries.accept(Items.DIAMOND_HELMET);
                        entries.accept(Items.DIAMOND_CHESTPLATE);
                        entries.accept(Items.DIAMOND_LEGGINGS);
                        entries.accept(Items.DIAMOND_BOOTS);
                        entries.accept(Items.NETHERITE_HELMET);
                        entries.accept(Items.NETHERITE_CHESTPLATE);
                        entries.accept(Items.NETHERITE_LEGGINGS);
                        entries.accept(Items.NETHERITE_BOOTS);
                        entries.accept(Items.TURTLE_HELMET);
                        entries.accept(Items.LEATHER_HORSE_ARMOR);
                        entries.accept(Items.IRON_HORSE_ARMOR);
                        entries.accept(Items.GOLDEN_HORSE_ARMOR);
                        entries.accept(Items.DIAMOND_HORSE_ARMOR);
                        entries.accept(Items.WOLF_ARMOR);
                        entries.accept(Items.TOTEM_OF_UNDYING);
                        entries.accept(Items.TNT);
                        entries.accept(Items.END_CRYSTAL);
                        entries.accept(Items.SNOWBALL);
                        entries.accept(Items.EGG);
                        entries.accept(Items.WIND_CHARGE);
                        entries.accept(Items.BOW);
                        entries.accept(Items.CROSSBOW);
                        generateFireworksAllDurations(entries, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                        entries.accept(Items.ARROW);
                        entries.accept(Items.SPECTRAL_ARROW);
                        displayContext.holders()
                            .lookup(Registries.POTION)
                            .ifPresent(
                                registryWrapper -> generatePotionEffectTypes(
                                        entries,
                                        registryWrapper,
                                        Items.TIPPED_ARROW,
                                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS,
                                        displayContext.enabledFeatures()
                                    )
                            );
                    }
                )
                .build()
        );
        Registry.register(
            registry,
            FOOD_AND_DRINKS,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 2)
                .title(Component.translatable("itemGroup.foodAndDrink"))
                .icon(() -> new ItemStack(Items.GOLDEN_APPLE))
                .displayItems(
                    (displayContext, entries) -> {
                        entries.accept(Items.APPLE);
                        entries.accept(Items.GOLDEN_APPLE);
                        entries.accept(Items.ENCHANTED_GOLDEN_APPLE);
                        entries.accept(Items.MELON_SLICE);
                        entries.accept(Items.SWEET_BERRIES);
                        entries.accept(Items.GLOW_BERRIES);
                        entries.accept(Items.CHORUS_FRUIT);
                        entries.accept(Items.CARROT);
                        entries.accept(Items.GOLDEN_CARROT);
                        entries.accept(Items.POTATO);
                        entries.accept(Items.BAKED_POTATO);
                        entries.accept(Items.POISONOUS_POTATO);
                        entries.accept(Items.BEETROOT);
                        entries.accept(Items.DRIED_KELP);
                        entries.accept(Items.BEEF);
                        entries.accept(Items.COOKED_BEEF);
                        entries.accept(Items.PORKCHOP);
                        entries.accept(Items.COOKED_PORKCHOP);
                        entries.accept(Items.MUTTON);
                        entries.accept(Items.COOKED_MUTTON);
                        entries.accept(Items.CHICKEN);
                        entries.accept(Items.COOKED_CHICKEN);
                        entries.accept(Items.RABBIT);
                        entries.accept(Items.COOKED_RABBIT);
                        entries.accept(Items.COD);
                        entries.accept(Items.COOKED_COD);
                        entries.accept(Items.SALMON);
                        entries.accept(Items.COOKED_SALMON);
                        entries.accept(Items.TROPICAL_FISH);
                        entries.accept(Items.PUFFERFISH);
                        entries.accept(Items.BREAD);
                        entries.accept(Items.COOKIE);
                        entries.accept(Items.CAKE);
                        entries.accept(Items.PUMPKIN_PIE);
                        entries.accept(Items.ROTTEN_FLESH);
                        entries.accept(Items.SPIDER_EYE);
                        entries.accept(Items.MUSHROOM_STEW);
                        entries.accept(Items.BEETROOT_SOUP);
                        entries.accept(Items.RABBIT_STEW);
                        generateSuspiciousStews(entries, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                        entries.accept(Items.MILK_BUCKET);
                        entries.accept(Items.HONEY_BOTTLE);
                        generateOminousVials(entries, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                        displayContext.holders()
                            .lookup(Registries.POTION)
                            .ifPresent(
                                registryWrapper -> {
                                    generatePotionEffectTypes(
                                        entries,
                                        registryWrapper,
                                        Items.POTION,
                                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS,
                                        displayContext.enabledFeatures()
                                    );
                                    generatePotionEffectTypes(
                                        entries,
                                        registryWrapper,
                                        Items.SPLASH_POTION,
                                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS,
                                        displayContext.enabledFeatures()
                                    );
                                    generatePotionEffectTypes(
                                        entries,
                                        registryWrapper,
                                        Items.LINGERING_POTION,
                                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS,
                                        displayContext.enabledFeatures()
                                    );
                                }
                            );
                    }
                )
                .build()
        );
        Registry.register(
            registry,
            INGREDIENTS,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 3)
                .title(Component.translatable("itemGroup.ingredients"))
                .icon(() -> new ItemStack(Items.IRON_INGOT))
                .displayItems((displayContext, entries) -> {
                    entries.accept(Items.COAL);
                    entries.accept(Items.CHARCOAL);
                    entries.accept(Items.RAW_IRON);
                    entries.accept(Items.RAW_COPPER);
                    entries.accept(Items.RAW_GOLD);
                    entries.accept(Items.EMERALD);
                    entries.accept(Items.LAPIS_LAZULI);
                    entries.accept(Items.DIAMOND);
                    entries.accept(Items.ANCIENT_DEBRIS);
                    entries.accept(Items.QUARTZ);
                    entries.accept(Items.AMETHYST_SHARD);
                    entries.accept(Items.IRON_NUGGET);
                    entries.accept(Items.GOLD_NUGGET);
                    entries.accept(Items.IRON_INGOT);
                    entries.accept(Items.COPPER_INGOT);
                    entries.accept(Items.GOLD_INGOT);
                    entries.accept(Items.NETHERITE_SCRAP);
                    entries.accept(Items.NETHERITE_INGOT);
                    entries.accept(Items.STICK);
                    entries.accept(Items.FLINT);
                    entries.accept(Items.WHEAT);
                    entries.accept(Items.BONE);
                    entries.accept(Items.BONE_MEAL);
                    entries.accept(Items.STRING);
                    entries.accept(Items.FEATHER);
                    entries.accept(Items.SNOWBALL);
                    entries.accept(Items.EGG);
                    entries.accept(Items.LEATHER);
                    entries.accept(Items.RABBIT_HIDE);
                    entries.accept(Items.HONEYCOMB);
                    entries.accept(Items.INK_SAC);
                    entries.accept(Items.GLOW_INK_SAC);
                    entries.accept(Items.TURTLE_SCUTE);
                    entries.accept(Items.ARMADILLO_SCUTE);
                    entries.accept(Items.SLIME_BALL);
                    entries.accept(Items.CLAY_BALL);
                    entries.accept(Items.PRISMARINE_SHARD);
                    entries.accept(Items.PRISMARINE_CRYSTALS);
                    entries.accept(Items.NAUTILUS_SHELL);
                    entries.accept(Items.HEART_OF_THE_SEA);
                    entries.accept(Items.FIRE_CHARGE);
                    entries.accept(Items.BLAZE_ROD);
                    entries.accept(Items.BREEZE_ROD);
                    entries.accept(Items.HEAVY_CORE);
                    entries.accept(Items.NETHER_STAR);
                    entries.accept(Items.ENDER_PEARL);
                    entries.accept(Items.ENDER_EYE);
                    entries.accept(Items.SHULKER_SHELL);
                    entries.accept(Items.POPPED_CHORUS_FRUIT);
                    entries.accept(Items.ECHO_SHARD);
                    entries.accept(Items.DISC_FRAGMENT_5);
                    entries.accept(Items.WHITE_DYE);
                    entries.accept(Items.LIGHT_GRAY_DYE);
                    entries.accept(Items.GRAY_DYE);
                    entries.accept(Items.BLACK_DYE);
                    entries.accept(Items.BROWN_DYE);
                    entries.accept(Items.RED_DYE);
                    entries.accept(Items.ORANGE_DYE);
                    entries.accept(Items.YELLOW_DYE);
                    entries.accept(Items.LIME_DYE);
                    entries.accept(Items.GREEN_DYE);
                    entries.accept(Items.CYAN_DYE);
                    entries.accept(Items.LIGHT_BLUE_DYE);
                    entries.accept(Items.BLUE_DYE);
                    entries.accept(Items.PURPLE_DYE);
                    entries.accept(Items.MAGENTA_DYE);
                    entries.accept(Items.PINK_DYE);
                    entries.accept(Items.BOWL);
                    entries.accept(Items.BRICK);
                    entries.accept(Items.NETHER_BRICK);
                    entries.accept(Items.PAPER);
                    entries.accept(Items.BOOK);
                    entries.accept(Items.FIREWORK_STAR);
                    entries.accept(Items.GLASS_BOTTLE);
                    entries.accept(Items.NETHER_WART);
                    entries.accept(Items.REDSTONE);
                    entries.accept(Items.GLOWSTONE_DUST);
                    entries.accept(Items.GUNPOWDER);
                    entries.accept(Items.DRAGON_BREATH);
                    entries.accept(Items.FERMENTED_SPIDER_EYE);
                    entries.accept(Items.BLAZE_POWDER);
                    entries.accept(Items.SUGAR);
                    entries.accept(Items.RABBIT_FOOT);
                    entries.accept(Items.GLISTERING_MELON_SLICE);
                    entries.accept(Items.SPIDER_EYE);
                    entries.accept(Items.PUFFERFISH);
                    entries.accept(Items.MAGMA_CREAM);
                    entries.accept(Items.GOLDEN_CARROT);
                    entries.accept(Items.GHAST_TEAR);
                    entries.accept(Items.TURTLE_HELMET);
                    entries.accept(Items.PHANTOM_MEMBRANE);
                    entries.accept(Items.FLOWER_BANNER_PATTERN);
                    entries.accept(Items.CREEPER_BANNER_PATTERN);
                    entries.accept(Items.SKULL_BANNER_PATTERN);
                    entries.accept(Items.MOJANG_BANNER_PATTERN);
                    entries.accept(Items.GLOBE_BANNER_PATTERN);
                    entries.accept(Items.PIGLIN_BANNER_PATTERN);
                    entries.accept(Items.FLOW_BANNER_PATTERN);
                    entries.accept(Items.GUSTER_BANNER_PATTERN);
                    entries.accept(Items.ANGLER_POTTERY_SHERD);
                    entries.accept(Items.ARCHER_POTTERY_SHERD);
                    entries.accept(Items.ARMS_UP_POTTERY_SHERD);
                    entries.accept(Items.BLADE_POTTERY_SHERD);
                    entries.accept(Items.BREWER_POTTERY_SHERD);
                    entries.accept(Items.BURN_POTTERY_SHERD);
                    entries.accept(Items.DANGER_POTTERY_SHERD);
                    entries.accept(Items.FLOW_POTTERY_SHERD);
                    entries.accept(Items.EXPLORER_POTTERY_SHERD);
                    entries.accept(Items.FRIEND_POTTERY_SHERD);
                    entries.accept(Items.GUSTER_POTTERY_SHERD);
                    entries.accept(Items.HEART_POTTERY_SHERD);
                    entries.accept(Items.HEARTBREAK_POTTERY_SHERD);
                    entries.accept(Items.HOWL_POTTERY_SHERD);
                    entries.accept(Items.MINER_POTTERY_SHERD);
                    entries.accept(Items.MOURNER_POTTERY_SHERD);
                    entries.accept(Items.PLENTY_POTTERY_SHERD);
                    entries.accept(Items.PRIZE_POTTERY_SHERD);
                    entries.accept(Items.SCRAPE_POTTERY_SHERD);
                    entries.accept(Items.SHEAF_POTTERY_SHERD);
                    entries.accept(Items.SHELTER_POTTERY_SHERD);
                    entries.accept(Items.SKULL_POTTERY_SHERD);
                    entries.accept(Items.SNORT_POTTERY_SHERD);
                    entries.accept(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
                    entries.accept(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE);
                    entries.accept(Items.EXPERIENCE_BOTTLE);
                    entries.accept(Items.TRIAL_KEY);
                    entries.accept(Items.OMINOUS_TRIAL_KEY);
                    displayContext.holders().lookup(Registries.ENCHANTMENT).ifPresent(registryWrapper -> {
                        generateEnchantmentBookTypesOnlyMaxLevel(entries, registryWrapper, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
                        generateEnchantmentBookTypesAllLevels(entries, registryWrapper, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
                    });
                })
                .build()
        );
        Registry.register(
            registry,
            SPAWN_EGGS,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 4)
                .title(Component.translatable("itemGroup.spawnEggs"))
                .icon(() -> new ItemStack(Items.PIG_SPAWN_EGG))
                .displayItems((displayContext, entries) -> {
                    entries.accept(Items.SPAWNER);
                    entries.accept(Items.TRIAL_SPAWNER);
                    entries.accept(Items.ALLAY_SPAWN_EGG);
                    entries.accept(Items.ARMADILLO_SPAWN_EGG);
                    entries.accept(Items.AXOLOTL_SPAWN_EGG);
                    entries.accept(Items.BAT_SPAWN_EGG);
                    entries.accept(Items.BEE_SPAWN_EGG);
                    entries.accept(Items.BLAZE_SPAWN_EGG);
                    entries.accept(Items.BOGGED_SPAWN_EGG);
                    entries.accept(Items.BREEZE_SPAWN_EGG);
                    entries.accept(Items.CAMEL_SPAWN_EGG);
                    entries.accept(Items.CAT_SPAWN_EGG);
                    entries.accept(Items.CAVE_SPIDER_SPAWN_EGG);
                    entries.accept(Items.CHICKEN_SPAWN_EGG);
                    entries.accept(Items.COD_SPAWN_EGG);
                    entries.accept(Items.COW_SPAWN_EGG);
                    entries.accept(Items.CREEPER_SPAWN_EGG);
                    entries.accept(Items.DOLPHIN_SPAWN_EGG);
                    entries.accept(Items.DONKEY_SPAWN_EGG);
                    entries.accept(Items.DROWNED_SPAWN_EGG);
                    entries.accept(Items.ELDER_GUARDIAN_SPAWN_EGG);
                    entries.accept(Items.ENDERMAN_SPAWN_EGG);
                    entries.accept(Items.ENDERMITE_SPAWN_EGG);
                    entries.accept(Items.EVOKER_SPAWN_EGG);
                    entries.accept(Items.FOX_SPAWN_EGG);
                    entries.accept(Items.FROG_SPAWN_EGG);
                    entries.accept(Items.GHAST_SPAWN_EGG);
                    entries.accept(Items.GLOW_SQUID_SPAWN_EGG);
                    entries.accept(Items.GOAT_SPAWN_EGG);
                    entries.accept(Items.GUARDIAN_SPAWN_EGG);
                    entries.accept(Items.HOGLIN_SPAWN_EGG);
                    entries.accept(Items.HORSE_SPAWN_EGG);
                    entries.accept(Items.HUSK_SPAWN_EGG);
                    entries.accept(Items.IRON_GOLEM_SPAWN_EGG);
                    entries.accept(Items.LLAMA_SPAWN_EGG);
                    entries.accept(Items.MAGMA_CUBE_SPAWN_EGG);
                    entries.accept(Items.MOOSHROOM_SPAWN_EGG);
                    entries.accept(Items.MULE_SPAWN_EGG);
                    entries.accept(Items.OCELOT_SPAWN_EGG);
                    entries.accept(Items.PANDA_SPAWN_EGG);
                    entries.accept(Items.PARROT_SPAWN_EGG);
                    entries.accept(Items.PHANTOM_SPAWN_EGG);
                    entries.accept(Items.PIG_SPAWN_EGG);
                    entries.accept(Items.PIGLIN_SPAWN_EGG);
                    entries.accept(Items.PIGLIN_BRUTE_SPAWN_EGG);
                    entries.accept(Items.PILLAGER_SPAWN_EGG);
                    entries.accept(Items.POLAR_BEAR_SPAWN_EGG);
                    entries.accept(Items.PUFFERFISH_SPAWN_EGG);
                    entries.accept(Items.RABBIT_SPAWN_EGG);
                    entries.accept(Items.RAVAGER_SPAWN_EGG);
                    entries.accept(Items.SALMON_SPAWN_EGG);
                    entries.accept(Items.SHEEP_SPAWN_EGG);
                    entries.accept(Items.SHULKER_SPAWN_EGG);
                    entries.accept(Items.SILVERFISH_SPAWN_EGG);
                    entries.accept(Items.SKELETON_SPAWN_EGG);
                    entries.accept(Items.SKELETON_HORSE_SPAWN_EGG);
                    entries.accept(Items.SLIME_SPAWN_EGG);
                    entries.accept(Items.SNIFFER_SPAWN_EGG);
                    entries.accept(Items.SNOW_GOLEM_SPAWN_EGG);
                    entries.accept(Items.SPIDER_SPAWN_EGG);
                    entries.accept(Items.SQUID_SPAWN_EGG);
                    entries.accept(Items.STRAY_SPAWN_EGG);
                    entries.accept(Items.STRIDER_SPAWN_EGG);
                    entries.accept(Items.TADPOLE_SPAWN_EGG);
                    entries.accept(Items.TRADER_LLAMA_SPAWN_EGG);
                    entries.accept(Items.TROPICAL_FISH_SPAWN_EGG);
                    entries.accept(Items.TURTLE_SPAWN_EGG);
                    entries.accept(Items.VEX_SPAWN_EGG);
                    entries.accept(Items.VILLAGER_SPAWN_EGG);
                    entries.accept(Items.VINDICATOR_SPAWN_EGG);
                    entries.accept(Items.WANDERING_TRADER_SPAWN_EGG);
                    entries.accept(Items.WARDEN_SPAWN_EGG);
                    entries.accept(Items.WITCH_SPAWN_EGG);
                    entries.accept(Items.WITHER_SKELETON_SPAWN_EGG);
                    entries.accept(Items.WOLF_SPAWN_EGG);
                    entries.accept(Items.ZOGLIN_SPAWN_EGG);
                    entries.accept(Items.ZOMBIE_SPAWN_EGG);
                    entries.accept(Items.ZOMBIE_HORSE_SPAWN_EGG);
                    entries.accept(Items.ZOMBIE_VILLAGER_SPAWN_EGG);
                    entries.accept(Items.ZOMBIFIED_PIGLIN_SPAWN_EGG);
                })
                .build()
        );
        Registry.register(
            registry,
            OP_BLOCKS,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 5)
                .title(Component.translatable("itemGroup.op"))
                .icon(() -> new ItemStack(Items.COMMAND_BLOCK))
                .alignedRight()
                .displayItems(
                    (displayContext, entries) -> {
                        if (displayContext.hasPermissions()) {
                            entries.accept(Items.COMMAND_BLOCK);
                            entries.accept(Items.CHAIN_COMMAND_BLOCK);
                            entries.accept(Items.REPEATING_COMMAND_BLOCK);
                            entries.accept(Items.COMMAND_BLOCK_MINECART);
                            entries.accept(Items.JIGSAW);
                            entries.accept(Items.STRUCTURE_BLOCK);
                            entries.accept(Items.STRUCTURE_VOID);
                            entries.accept(Items.BARRIER);
                            entries.accept(Items.DEBUG_STICK);

                            for (int i = 15; i >= 0; i--) {
                                entries.accept(LightBlock.setLightOnStack(new ItemStack(Items.LIGHT), i));
                            }

                            displayContext.holders()
                                .lookup(Registries.PAINTING_VARIANT)
                                .ifPresent(
                                    registryWrapper -> generatePresetPaintings(
                                            entries,
                                            displayContext.holders(),
                                            (HolderLookup.RegistryLookup<PaintingVariant>)registryWrapper,
                                            registryEntry -> !registryEntry.is(PaintingVariantTags.PLACEABLE),
                                            CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
                                        )
                                );
                        }
                    }
                )
                .build()
        );
        return Registry.register(
            registry,
            INVENTORY,
            CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 6)
                .title(Component.translatable("itemGroup.inventory"))
                .icon(() -> new ItemStack(Blocks.CHEST))
                .backgroundTexture(INVENTORY_BACKGROUND)
                .hideTitle()
                .alignedRight()
                .type(CreativeModeTab.Type.INVENTORY)
                .noScrollBar()
                .build()
        );
    }

    public static void validate() {
        Map<Pair<CreativeModeTab.Row, Integer>, String> map = new HashMap<>();

        for (ResourceKey<CreativeModeTab> resourceKey : BuiltInRegistries.CREATIVE_MODE_TAB.registryKeySet()) {
            CreativeModeTab creativeModeTab = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(resourceKey);
            String string = creativeModeTab.getDisplayName().getString();
            String string2 = map.put(Pair.of(creativeModeTab.row(), creativeModeTab.column()), string);
            if (string2 != null) {
                throw new IllegalArgumentException("Duplicate position: " + string + " vs. " + string2);
            }
        }
    }

    public static CreativeModeTab getDefaultTab() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(BUILDING_BLOCKS);
    }

    private static void generatePotionEffectTypes(
        CreativeModeTab.Output entries,
        HolderLookup<Potion> registryWrapper,
        Item item,
        CreativeModeTab.TabVisibility visibility,
        FeatureFlagSet enabledFeatures
    ) {
        registryWrapper.listElements()
            .filter(potionEntry -> potionEntry.value().isEnabled(enabledFeatures))
            .map(entry -> PotionContents.createItemStack(item, entry))
            .forEach(stack -> entries.accept(stack, visibility));
    }

    private static void generateEnchantmentBookTypesOnlyMaxLevel(
        CreativeModeTab.Output entries, HolderLookup<Enchantment> registryWrapper, CreativeModeTab.TabVisibility stackVisibility
    ) {
        registryWrapper.listElements()
            .map(enchantmentEntry -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantmentEntry, enchantmentEntry.value().getMaxLevel())))
            .forEach(stack -> entries.accept(stack, stackVisibility));
    }

    private static void generateEnchantmentBookTypesAllLevels(
        CreativeModeTab.Output entries, HolderLookup<Enchantment> registryWrapper, CreativeModeTab.TabVisibility stackVisibility
    ) {
        registryWrapper.listElements()
            .flatMap(
                enchantmentEntry -> IntStream.rangeClosed(enchantmentEntry.value().getMinLevel(), enchantmentEntry.value().getMaxLevel())
                        .mapToObj(level -> EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantmentEntry, level)))
            )
            .forEach(stack -> entries.accept(stack, stackVisibility));
    }

    private static void generateInstrumentTypes(
        CreativeModeTab.Output entries,
        HolderLookup<Instrument> registryWrapper,
        Item item,
        TagKey<Instrument> instrumentTag,
        CreativeModeTab.TabVisibility visibility
    ) {
        registryWrapper.get(instrumentTag)
            .ifPresent(
                entryList -> entryList.stream()
                        .map(instrument -> InstrumentItem.create(item, (Holder<Instrument>)instrument))
                        .forEach(stack -> entries.accept(stack, visibility))
            );
    }

    private static void generateSuspiciousStews(CreativeModeTab.Output entries, CreativeModeTab.TabVisibility visibility) {
        List<SuspiciousEffectHolder> list = SuspiciousEffectHolder.getAllEffectHolders();
        Set<ItemStack> set = ItemStackLinkedSet.createTypeAndComponentsSet();

        for (SuspiciousEffectHolder suspiciousEffectHolder : list) {
            ItemStack itemStack = new ItemStack(Items.SUSPICIOUS_STEW);
            itemStack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, suspiciousEffectHolder.getSuspiciousEffects());
            set.add(itemStack);
        }

        entries.acceptAll(set, visibility);
    }

    private static void generateOminousVials(CreativeModeTab.Output entries, CreativeModeTab.TabVisibility visibility) {
        for (int i = 0; i <= 4; i++) {
            ItemStack itemStack = new ItemStack(Items.OMINOUS_BOTTLE);
            itemStack.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, i);
            entries.accept(itemStack, visibility);
        }
    }

    private static void generateFireworksAllDurations(CreativeModeTab.Output entries, CreativeModeTab.TabVisibility visibility) {
        for (byte b : FireworkRocketItem.CRAFTABLE_DURATIONS) {
            ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
            itemStack.set(DataComponents.FIREWORKS, new Fireworks(b, List.of()));
            entries.accept(itemStack, visibility);
        }
    }

    private static void generatePresetPaintings(
        CreativeModeTab.Output entries,
        HolderLookup.Provider registryLookup,
        HolderLookup.RegistryLookup<PaintingVariant> registryWrapper,
        Predicate<Holder<PaintingVariant>> filter,
        CreativeModeTab.TabVisibility stackVisibility
    ) {
        RegistryOps<Tag> registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        registryWrapper.listElements()
            .filter(filter)
            .sorted(PAINTING_COMPARATOR)
            .forEach(
                paintingVariantEntry -> {
                    CustomData customData = CustomData.EMPTY
                        .update(registryOps, Painting.VARIANT_MAP_CODEC, paintingVariantEntry)
                        .getOrThrow()
                        .update(nbt -> nbt.putString("id", "minecraft:painting"));
                    ItemStack itemStack = new ItemStack(Items.PAINTING);
                    itemStack.set(DataComponents.ENTITY_DATA, customData);
                    entries.accept(itemStack, stackVisibility);
                }
            );
    }

    public static List<CreativeModeTab> tabs() {
        return streamAllTabs().filter(CreativeModeTab::shouldDisplay).toList();
    }

    public static List<CreativeModeTab> allTabs() {
        return streamAllTabs().toList();
    }

    private static Stream<CreativeModeTab> streamAllTabs() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.stream();
    }

    public static CreativeModeTab searchTab() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(SEARCH);
    }

    private static void buildAllTabContents(CreativeModeTab.ItemDisplayParameters displayContext) {
        streamAllTabs().filter(group -> group.getType() == CreativeModeTab.Type.CATEGORY).forEach(group -> group.buildContents(displayContext));
        streamAllTabs().filter(group -> group.getType() != CreativeModeTab.Type.CATEGORY).forEach(group -> group.buildContents(displayContext));
    }

    public static boolean tryRebuildTabContents(FeatureFlagSet enabledFeatures, boolean operatorEnabled, HolderLookup.Provider lookup) {
        if (CACHED_PARAMETERS != null && !CACHED_PARAMETERS.needsUpdate(enabledFeatures, operatorEnabled, lookup)) {
            return false;
        } else {
            CACHED_PARAMETERS = new CreativeModeTab.ItemDisplayParameters(enabledFeatures, operatorEnabled, lookup);
            buildAllTabContents(CACHED_PARAMETERS);
            return true;
        }
    }
}
