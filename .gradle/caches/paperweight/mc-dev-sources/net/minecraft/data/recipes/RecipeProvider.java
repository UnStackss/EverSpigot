package net.minecraft.data.recipes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.EnterBlockTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public abstract class RecipeProvider implements DataProvider {
    final PackOutput.PathProvider recipePathProvider;
    final PackOutput.PathProvider advancementPathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;
    private static final Map<BlockFamily.Variant, BiFunction<ItemLike, ItemLike, RecipeBuilder>> SHAPE_BUILDERS = ImmutableMap.<BlockFamily.Variant, BiFunction<ItemLike, ItemLike, RecipeBuilder>>builder()
        .put(BlockFamily.Variant.BUTTON, (output, input) -> buttonBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.CHISELED, (output, input) -> chiseledBuilder(RecipeCategory.BUILDING_BLOCKS, output, Ingredient.of(input)))
        .put(BlockFamily.Variant.CUT, (output, input) -> cutBuilder(RecipeCategory.BUILDING_BLOCKS, output, Ingredient.of(input)))
        .put(BlockFamily.Variant.DOOR, (output, input) -> doorBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.CUSTOM_FENCE, (output, input) -> fenceBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.FENCE, (output, input) -> fenceBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.CUSTOM_FENCE_GATE, (output, input) -> fenceGateBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.FENCE_GATE, (output, input) -> fenceGateBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.SIGN, (output, input) -> signBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.SLAB, (output, input) -> slabBuilder(RecipeCategory.BUILDING_BLOCKS, output, Ingredient.of(input)))
        .put(BlockFamily.Variant.STAIRS, (output, input) -> stairBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.PRESSURE_PLATE, (output, input) -> pressurePlateBuilder(RecipeCategory.REDSTONE, output, Ingredient.of(input)))
        .put(BlockFamily.Variant.POLISHED, (output, input) -> polishedBuilder(RecipeCategory.BUILDING_BLOCKS, output, Ingredient.of(input)))
        .put(BlockFamily.Variant.TRAPDOOR, (output, input) -> trapdoorBuilder(output, Ingredient.of(input)))
        .put(BlockFamily.Variant.WALL, (output, input) -> wallBuilder(RecipeCategory.DECORATIONS, output, Ingredient.of(input)))
        .build();

    public RecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        this.recipePathProvider = output.createRegistryElementsPathProvider(Registries.RECIPE);
        this.advancementPathProvider = output.createRegistryElementsPathProvider(Registries.ADVANCEMENT);
        this.registries = registryLookupFuture;
    }

    @Override
    public final CompletableFuture<?> run(CachedOutput writer) {
        return this.registries.thenCompose(registryLookup -> this.run(writer, registryLookup));
    }

    protected CompletableFuture<?> run(CachedOutput writer, HolderLookup.Provider registryLookup) {
        final Set<ResourceLocation> set = Sets.newHashSet();
        final List<CompletableFuture<?>> list = new ArrayList<>();
        this.buildRecipes(
            new RecipeOutput() {
                @Override
                public void accept(ResourceLocation recipeId, Recipe<?> recipe, @Nullable AdvancementHolder advancement) {
                    if (!set.add(recipeId)) {
                        throw new IllegalStateException("Duplicate recipe " + recipeId);
                    } else {
                        list.add(DataProvider.saveStable(writer, registryLookup, Recipe.CODEC, recipe, RecipeProvider.this.recipePathProvider.json(recipeId)));
                        if (advancement != null) {
                            list.add(
                                DataProvider.saveStable(
                                    writer,
                                    registryLookup,
                                    Advancement.CODEC,
                                    advancement.value(),
                                    RecipeProvider.this.advancementPathProvider.json(advancement.id())
                                )
                            );
                        }
                    }
                }

                @Override
                public Advancement.Builder advancement() {
                    return Advancement.Builder.recipeAdvancement().parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
                }
            }
        );
        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
    }

    protected CompletableFuture<?> buildAdvancement(CachedOutput cache, HolderLookup.Provider registryLookup, AdvancementHolder advancement) {
        return DataProvider.saveStable(cache, registryLookup, Advancement.CODEC, advancement.value(), this.advancementPathProvider.json(advancement.id()));
    }

    protected abstract void buildRecipes(RecipeOutput exporter);

    protected static void generateForEnabledBlockFamilies(RecipeOutput exporter, FeatureFlagSet enabledFeatures) {
        BlockFamilies.getAllFamilies().filter(BlockFamily::shouldGenerateRecipe).forEach(family -> generateRecipes(exporter, family, enabledFeatures));
    }

    protected static void oneToOneConversionRecipe(RecipeOutput exporter, ItemLike output, ItemLike input, @Nullable String group) {
        oneToOneConversionRecipe(exporter, output, input, group, 1);
    }

    protected static void oneToOneConversionRecipe(RecipeOutput exporter, ItemLike output, ItemLike input, @Nullable String group, int outputCount) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, output, outputCount)
            .requires(input)
            .group(group)
            .unlockedBy(getHasName(input), has(input))
            .save(exporter, getConversionRecipeName(output, input));
    }

    protected static void oreSmelting(
        RecipeOutput exporter, List<ItemLike> inputs, RecipeCategory category, ItemLike output, float experience, int cookingTime, String group
    ) {
        oreCooking(exporter, RecipeSerializer.SMELTING_RECIPE, SmeltingRecipe::new, inputs, category, output, experience, cookingTime, group, "_from_smelting");
    }

    protected static void oreBlasting(
        RecipeOutput exporter, List<ItemLike> inputs, RecipeCategory category, ItemLike output, float experience, int cookingTime, String group
    ) {
        oreCooking(exporter, RecipeSerializer.BLASTING_RECIPE, BlastingRecipe::new, inputs, category, output, experience, cookingTime, group, "_from_blasting");
    }

    private static <T extends AbstractCookingRecipe> void oreCooking(
        RecipeOutput exporter,
        RecipeSerializer<T> serializer,
        AbstractCookingRecipe.Factory<T> recipeFactory,
        List<ItemLike> inputs,
        RecipeCategory category,
        ItemLike output,
        float experience,
        int cookingTime,
        String group,
        String suffix
    ) {
        for (ItemLike itemLike : inputs) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemLike), category, output, experience, cookingTime, serializer, recipeFactory)
                .group(group)
                .unlockedBy(getHasName(itemLike), has(itemLike))
                .save(exporter, getItemName(output) + suffix + "_" + getItemName(itemLike));
        }
    }

    protected static void netheriteSmithing(RecipeOutput exporter, Item input, RecipeCategory category, Item result) {
        SmithingTransformRecipeBuilder.smithing(
                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), Ingredient.of(input), Ingredient.of(Items.NETHERITE_INGOT), category, result
            )
            .unlocks("has_netherite_ingot", has(Items.NETHERITE_INGOT))
            .save(exporter, getItemName(result) + "_smithing");
    }

    protected static void trimSmithing(RecipeOutput exporter, Item template, ResourceLocation recipeId) {
        SmithingTrimRecipeBuilder.smithingTrim(
                Ingredient.of(template), Ingredient.of(ItemTags.TRIMMABLE_ARMOR), Ingredient.of(ItemTags.TRIM_MATERIALS), RecipeCategory.MISC
            )
            .unlocks("has_smithing_trim_template", has(template))
            .save(exporter, recipeId);
    }

    protected static void twoByTwoPacker(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(category, output, 1).define('#', input).pattern("##").pattern("##").unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    protected static void threeByThreePacker(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input, String criterionName) {
        ShapelessRecipeBuilder.shapeless(category, output).requires(input, 9).unlockedBy(criterionName, has(input)).save(exporter);
    }

    protected static void threeByThreePacker(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        threeByThreePacker(exporter, category, output, input, getHasName(input));
    }

    protected static void planksFromLog(RecipeOutput exporter, ItemLike output, TagKey<Item> input, int count) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, output, count)
            .requires(input)
            .group("planks")
            .unlockedBy("has_log", has(input))
            .save(exporter);
    }

    protected static void planksFromLogs(RecipeOutput exporter, ItemLike output, TagKey<Item> input, int count) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, output, count)
            .requires(input)
            .group("planks")
            .unlockedBy("has_logs", has(input))
            .save(exporter);
    }

    protected static void woodFromLogs(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, output, 3)
            .define('#', input)
            .pattern("##")
            .pattern("##")
            .group("bark")
            .unlockedBy("has_log", has(input))
            .save(exporter);
    }

    protected static void woodenBoat(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, output)
            .define('#', input)
            .pattern("# #")
            .pattern("###")
            .group("boat")
            .unlockedBy("in_water", insideOf(Blocks.WATER))
            .save(exporter);
    }

    protected static void chestBoat(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TRANSPORTATION, output)
            .requires(Blocks.CHEST)
            .requires(input)
            .group("chest_boat")
            .unlockedBy("has_boat", has(ItemTags.BOATS))
            .save(exporter);
    }

    private static RecipeBuilder buttonBuilder(ItemLike output, Ingredient input) {
        return ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, output).requires(input);
    }

    protected static RecipeBuilder doorBuilder(ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, output, 3).define('#', input).pattern("##").pattern("##").pattern("##");
    }

    private static RecipeBuilder fenceBuilder(ItemLike output, Ingredient input) {
        int i = output == Blocks.NETHER_BRICK_FENCE ? 6 : 3;
        Item item = output == Blocks.NETHER_BRICK_FENCE ? Items.NETHER_BRICK : Items.STICK;
        return ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output, i).define('W', input).define('#', item).pattern("W#W").pattern("W#W");
    }

    private static RecipeBuilder fenceGateBuilder(ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, output).define('#', Items.STICK).define('W', input).pattern("#W#").pattern("#W#");
    }

    protected static void pressurePlate(RecipeOutput exporter, ItemLike output, ItemLike input) {
        pressurePlateBuilder(RecipeCategory.REDSTONE, output, Ingredient.of(input)).unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    private static RecipeBuilder pressurePlateBuilder(RecipeCategory category, ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(category, output).define('#', input).pattern("##");
    }

    protected static void slab(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        slabBuilder(category, output, Ingredient.of(input)).unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    protected static RecipeBuilder slabBuilder(RecipeCategory category, ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(category, output, 6).define('#', input).pattern("###");
    }

    protected static RecipeBuilder stairBuilder(ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, output, 4).define('#', input).pattern("#  ").pattern("## ").pattern("###");
    }

    protected static RecipeBuilder trapdoorBuilder(ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, output, 2).define('#', input).pattern("###").pattern("###");
    }

    private static RecipeBuilder signBuilder(ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output, 3)
            .group("sign")
            .define('#', input)
            .define('X', Items.STICK)
            .pattern("###")
            .pattern("###")
            .pattern(" X ");
    }

    protected static void hangingSign(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output, 6)
            .group("hanging_sign")
            .define('#', input)
            .define('X', Items.CHAIN)
            .pattern("X X")
            .pattern("###")
            .pattern("###")
            .unlockedBy("has_stripped_logs", has(input))
            .save(exporter);
    }

    protected static void colorBlockWithDye(RecipeOutput exporter, List<Item> dyes, List<Item> dyeables, String group) {
        for (int i = 0; i < dyes.size(); i++) {
            Item item = dyes.get(i);
            Item item2 = dyeables.get(i);
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, item2)
                .requires(item)
                .requires(Ingredient.of(dyeables.stream().filter(dyeable -> !dyeable.equals(item2)).map(ItemStack::new)))
                .group(group)
                .unlockedBy("has_needed_dye", has(item))
                .save(exporter, "dye_" + getItemName(item2));
        }
    }

    protected static void carpet(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output, 3)
            .define('#', input)
            .pattern("##")
            .group("carpet")
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void bedFromPlanksAndWool(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output)
            .define('#', input)
            .define('X', ItemTags.PLANKS)
            .pattern("###")
            .pattern("XXX")
            .group("bed")
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void banner(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output)
            .define('#', input)
            .define('|', Items.STICK)
            .pattern("###")
            .pattern("###")
            .pattern(" | ")
            .group("banner")
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void stainedGlassFromGlassAndDye(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, output, 8)
            .define('#', Blocks.GLASS)
            .define('X', input)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("stained_glass")
            .unlockedBy("has_glass", has(Blocks.GLASS))
            .save(exporter);
    }

    protected static void stainedGlassPaneFromStainedGlass(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output, 16)
            .define('#', input)
            .pattern("###")
            .pattern("###")
            .group("stained_glass_pane")
            .unlockedBy("has_glass", has(input))
            .save(exporter);
    }

    protected static void stainedGlassPaneFromGlassPaneAndDye(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, output, 8)
            .define('#', Blocks.GLASS_PANE)
            .define('$', input)
            .pattern("###")
            .pattern("#$#")
            .pattern("###")
            .group("stained_glass_pane")
            .unlockedBy("has_glass_pane", has(Blocks.GLASS_PANE))
            .unlockedBy(getHasName(input), has(input))
            .save(exporter, getConversionRecipeName(output, Blocks.GLASS_PANE));
    }

    protected static void coloredTerracottaFromTerracottaAndDye(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, output, 8)
            .define('#', Blocks.TERRACOTTA)
            .define('X', input)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("stained_terracotta")
            .unlockedBy("has_terracotta", has(Blocks.TERRACOTTA))
            .save(exporter);
    }

    protected static void concretePowder(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, output, 8)
            .requires(input)
            .requires(Blocks.SAND, 4)
            .requires(Blocks.GRAVEL, 4)
            .group("concrete_powder")
            .unlockedBy("has_sand", has(Blocks.SAND))
            .unlockedBy("has_gravel", has(Blocks.GRAVEL))
            .save(exporter);
    }

    protected static void candle(RecipeOutput exporter, ItemLike output, ItemLike input) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, output)
            .requires(Blocks.CANDLE)
            .requires(input)
            .group("dyed_candle")
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void wall(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        wallBuilder(category, output, Ingredient.of(input)).unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    private static RecipeBuilder wallBuilder(RecipeCategory category, ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(category, output, 6).define('#', input).pattern("###").pattern("###");
    }

    protected static void polished(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        polishedBuilder(category, output, Ingredient.of(input)).unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    private static RecipeBuilder polishedBuilder(RecipeCategory category, ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(category, output, 4).define('S', input).pattern("SS").pattern("SS");
    }

    protected static void cut(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        cutBuilder(category, output, Ingredient.of(input)).unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    private static ShapedRecipeBuilder cutBuilder(RecipeCategory category, ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(category, output, 4).define('#', input).pattern("##").pattern("##");
    }

    protected static void chiseled(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        chiseledBuilder(category, output, Ingredient.of(input)).unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    protected static void mosaicBuilder(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        ShapedRecipeBuilder.shaped(category, output).define('#', input).pattern("#").pattern("#").unlockedBy(getHasName(input), has(input)).save(exporter);
    }

    protected static ShapedRecipeBuilder chiseledBuilder(RecipeCategory category, ItemLike output, Ingredient input) {
        return ShapedRecipeBuilder.shaped(category, output).define('#', input).pattern("#").pattern("#");
    }

    protected static void stonecutterResultFromBase(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input) {
        stonecutterResultFromBase(exporter, category, output, input, 1);
    }

    protected static void stonecutterResultFromBase(RecipeOutput exporter, RecipeCategory category, ItemLike output, ItemLike input, int count) {
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(input), category, output, count)
            .unlockedBy(getHasName(input), has(input))
            .save(exporter, getConversionRecipeName(output, input) + "_stonecutting");
    }

    private static void smeltingResultFromBase(RecipeOutput exporter, ItemLike output, ItemLike input) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(input), RecipeCategory.BUILDING_BLOCKS, output, 0.1F, 200)
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void nineBlockStorageRecipes(
        RecipeOutput exporter, RecipeCategory reverseCategory, ItemLike baseItem, RecipeCategory compactingCategory, ItemLike compactItem
    ) {
        nineBlockStorageRecipes(
            exporter, reverseCategory, baseItem, compactingCategory, compactItem, getSimpleRecipeName(compactItem), null, getSimpleRecipeName(baseItem), null
        );
    }

    protected static void nineBlockStorageRecipesWithCustomPacking(
        RecipeOutput exporter,
        RecipeCategory reverseCategory,
        ItemLike baseItem,
        RecipeCategory compactingCategory,
        ItemLike compactItem,
        String compactingId,
        String compactingGroup
    ) {
        nineBlockStorageRecipes(
            exporter, reverseCategory, baseItem, compactingCategory, compactItem, compactingId, compactingGroup, getSimpleRecipeName(baseItem), null
        );
    }

    protected static void nineBlockStorageRecipesRecipesWithCustomUnpacking(
        RecipeOutput exporter,
        RecipeCategory reverseCategory,
        ItemLike baseItem,
        RecipeCategory compactingCategory,
        ItemLike compactItem,
        String reverseId,
        String reverseGroup
    ) {
        nineBlockStorageRecipes(
            exporter, reverseCategory, baseItem, compactingCategory, compactItem, getSimpleRecipeName(compactItem), null, reverseId, reverseGroup
        );
    }

    private static void nineBlockStorageRecipes(
        RecipeOutput exporter,
        RecipeCategory reverseCategory,
        ItemLike baseItem,
        RecipeCategory compactingCategory,
        ItemLike compactItem,
        String compactingId,
        @Nullable String compactingGroup,
        String reverseId,
        @Nullable String reverseGroup
    ) {
        ShapelessRecipeBuilder.shapeless(reverseCategory, baseItem, 9)
            .requires(compactItem)
            .group(reverseGroup)
            .unlockedBy(getHasName(compactItem), has(compactItem))
            .save(exporter, ResourceLocation.parse(reverseId));
        ShapedRecipeBuilder.shaped(compactingCategory, compactItem)
            .define('#', baseItem)
            .pattern("###")
            .pattern("###")
            .pattern("###")
            .group(compactingGroup)
            .unlockedBy(getHasName(baseItem), has(baseItem))
            .save(exporter, ResourceLocation.parse(compactingId));
    }

    protected static void copySmithingTemplate(RecipeOutput exporter, ItemLike template, TagKey<Item> resource) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', resource)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), has(template))
            .save(exporter);
    }

    protected static void copySmithingTemplate(RecipeOutput exporter, ItemLike template, ItemLike resource) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', resource)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), has(template))
            .save(exporter);
    }

    protected static void copySmithingTemplate(RecipeOutput exporter, ItemLike template, Ingredient resource) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', resource)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), has(template))
            .save(exporter);
    }

    protected static <T extends AbstractCookingRecipe> void cookRecipes(
        RecipeOutput exporter, String cooker, RecipeSerializer<T> serializer, AbstractCookingRecipe.Factory<T> recipeFactory, int cookingTime
    ) {
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.BEEF, Items.COOKED_BEEF, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.CHICKEN, Items.COOKED_CHICKEN, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.COD, Items.COOKED_COD, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.KELP, Items.DRIED_KELP, 0.1F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.SALMON, Items.COOKED_SALMON, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.MUTTON, Items.COOKED_MUTTON, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.PORKCHOP, Items.COOKED_PORKCHOP, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.POTATO, Items.BAKED_POTATO, 0.35F);
        simpleCookingRecipe(exporter, cooker, serializer, recipeFactory, cookingTime, Items.RABBIT, Items.COOKED_RABBIT, 0.35F);
    }

    private static <T extends AbstractCookingRecipe> void simpleCookingRecipe(
        RecipeOutput exporter,
        String cooker,
        RecipeSerializer<T> serializer,
        AbstractCookingRecipe.Factory<T> recipeFactory,
        int cookingTime,
        ItemLike items,
        ItemLike output,
        float experience
    ) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(items), RecipeCategory.FOOD, output, experience, cookingTime, serializer, recipeFactory)
            .unlockedBy(getHasName(items), has(items))
            .save(exporter, getItemName(output) + "_from_" + cooker);
    }

    protected static void waxRecipes(RecipeOutput exporter, FeatureFlagSet enabledFeatures) {
        HoneycombItem.WAXABLES
            .get()
            .forEach(
                (unwaxed, waxed) -> {
                    if (waxed.requiredFeatures().isSubsetOf(enabledFeatures)) {
                        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, waxed)
                            .requires(unwaxed)
                            .requires(Items.HONEYCOMB)
                            .group(getItemName(waxed))
                            .unlockedBy(getHasName(unwaxed), has(unwaxed))
                            .save(exporter, getConversionRecipeName(waxed, Items.HONEYCOMB));
                    }
                }
            );
    }

    protected static void grate(RecipeOutput exporter, Block output, Block input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, output, 4)
            .define('M', input)
            .pattern(" M ")
            .pattern("M M")
            .pattern(" M ")
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void copperBulb(RecipeOutput exporter, Block output, Block input) {
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, output, 4)
            .define('C', input)
            .define('R', Items.REDSTONE)
            .define('B', Items.BLAZE_ROD)
            .pattern(" C ")
            .pattern("CBC")
            .pattern(" R ")
            .unlockedBy(getHasName(input), has(input))
            .save(exporter);
    }

    protected static void generateRecipes(RecipeOutput exporter, BlockFamily family, FeatureFlagSet enabledFeatures) {
        family.getVariants()
            .forEach(
                (variant, block) -> {
                    if (block.requiredFeatures().isSubsetOf(enabledFeatures)) {
                        BiFunction<ItemLike, ItemLike, RecipeBuilder> biFunction = SHAPE_BUILDERS.get(variant);
                        ItemLike itemLike = getBaseBlock(family, variant);
                        if (biFunction != null) {
                            RecipeBuilder recipeBuilder = biFunction.apply(block, itemLike);
                            family.getRecipeGroupPrefix()
                                .ifPresent(group -> recipeBuilder.group(group + (variant == BlockFamily.Variant.CUT ? "" : "_" + variant.getRecipeGroup())));
                            recipeBuilder.unlockedBy(family.getRecipeUnlockedBy().orElseGet(() -> getHasName(itemLike)), has(itemLike));
                            recipeBuilder.save(exporter);
                        }

                        if (variant == BlockFamily.Variant.CRACKED) {
                            smeltingResultFromBase(exporter, block, itemLike);
                        }
                    }
                }
            );
    }

    private static Block getBaseBlock(BlockFamily family, BlockFamily.Variant variant) {
        if (variant == BlockFamily.Variant.CHISELED) {
            if (!family.getVariants().containsKey(BlockFamily.Variant.SLAB)) {
                throw new IllegalStateException("Slab is not defined for the family.");
            } else {
                return family.get(BlockFamily.Variant.SLAB);
            }
        } else {
            return family.getBaseBlock();
        }
    }

    private static Criterion<EnterBlockTrigger.TriggerInstance> insideOf(Block block) {
        return CriteriaTriggers.ENTER_BLOCK
            .createCriterion(new EnterBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> has(MinMaxBounds.Ints count, ItemLike item) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(item).withCount(count));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> has(ItemLike item) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(item));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> has(TagKey<Item> tag) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(tag));
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate.Builder... predicates) {
        return inventoryTrigger(Arrays.stream(predicates).map(ItemPredicate.Builder::build).toArray(ItemPredicate[]::new));
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate... predicates) {
        return CriteriaTriggers.INVENTORY_CHANGED
            .createCriterion(
                new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(predicates))
            );
    }

    protected static String getHasName(ItemLike item) {
        return "has_" + getItemName(item);
    }

    protected static String getItemName(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
    }

    protected static String getSimpleRecipeName(ItemLike item) {
        return getItemName(item);
    }

    protected static String getConversionRecipeName(ItemLike to, ItemLike from) {
        return getItemName(to) + "_from_" + getItemName(from);
    }

    protected static String getSmeltingRecipeName(ItemLike item) {
        return getItemName(item) + "_from_smelting";
    }

    protected static String getBlastingRecipeName(ItemLike item) {
        return getItemName(item) + "_from_blasting";
    }

    @Override
    public final String getName() {
        return "Recipes";
    }
}
