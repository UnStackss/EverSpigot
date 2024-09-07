package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;

public class SimpleCookingRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final CookingBookCategory bookCategory;
    private final Item result;
    private final Ingredient ingredient;
    private final float experience;
    private final int cookingTime;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    @Nullable
    private String group;
    private final AbstractCookingRecipe.Factory<?> factory;

    private SimpleCookingRecipeBuilder(
        RecipeCategory category,
        CookingBookCategory cookingCategory,
        ItemLike output,
        Ingredient input,
        float experience,
        int cookingTime,
        AbstractCookingRecipe.Factory<?> recipeFactory
    ) {
        this.category = category;
        this.bookCategory = cookingCategory;
        this.result = output.asItem();
        this.ingredient = input;
        this.experience = experience;
        this.cookingTime = cookingTime;
        this.factory = recipeFactory;
    }

    public static <T extends AbstractCookingRecipe> SimpleCookingRecipeBuilder generic(
        Ingredient input,
        RecipeCategory category,
        ItemLike output,
        float experience,
        int cookingTime,
        RecipeSerializer<T> serializer,
        AbstractCookingRecipe.Factory<T> recipeFactory
    ) {
        return new SimpleCookingRecipeBuilder(category, determineRecipeCategory(serializer, output), output, input, experience, cookingTime, recipeFactory);
    }

    public static SimpleCookingRecipeBuilder campfireCooking(Ingredient input, RecipeCategory category, ItemLike output, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, CookingBookCategory.FOOD, output, input, experience, cookingTime, CampfireCookingRecipe::new);
    }

    public static SimpleCookingRecipeBuilder blasting(Ingredient input, RecipeCategory category, ItemLike output, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, determineBlastingRecipeCategory(output), output, input, experience, cookingTime, BlastingRecipe::new);
    }

    public static SimpleCookingRecipeBuilder smelting(Ingredient input, RecipeCategory category, ItemLike output, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, determineSmeltingRecipeCategory(output), output, input, experience, cookingTime, SmeltingRecipe::new);
    }

    public static SimpleCookingRecipeBuilder smoking(Ingredient input, RecipeCategory category, ItemLike output, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, CookingBookCategory.FOOD, output, input, experience, cookingTime, SmokingRecipe::new);
    }

    @Override
    public SimpleCookingRecipeBuilder unlockedBy(String string, Criterion<?> criterion) {
        this.criteria.put(string, criterion);
        return this;
    }

    @Override
    public SimpleCookingRecipeBuilder group(@Nullable String string) {
        this.group = string;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result;
    }

    @Override
    public void save(RecipeOutput exporter, ResourceLocation recipeId) {
        this.ensureValid(recipeId);
        Advancement.Builder builder = exporter.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeId))
            .rewards(AdvancementRewards.Builder.recipe(recipeId))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        AbstractCookingRecipe abstractCookingRecipe = this.factory
            .create(
                Objects.requireNonNullElse(this.group, ""), this.bookCategory, this.ingredient, new ItemStack(this.result), this.experience, this.cookingTime
            );
        exporter.accept(recipeId, abstractCookingRecipe, builder.build(recipeId.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private static CookingBookCategory determineSmeltingRecipeCategory(ItemLike output) {
        if (output.asItem().components().has(DataComponents.FOOD)) {
            return CookingBookCategory.FOOD;
        } else {
            return output.asItem() instanceof BlockItem ? CookingBookCategory.BLOCKS : CookingBookCategory.MISC;
        }
    }

    private static CookingBookCategory determineBlastingRecipeCategory(ItemLike output) {
        return output.asItem() instanceof BlockItem ? CookingBookCategory.BLOCKS : CookingBookCategory.MISC;
    }

    private static CookingBookCategory determineRecipeCategory(RecipeSerializer<? extends AbstractCookingRecipe> serializer, ItemLike output) {
        if (serializer == RecipeSerializer.SMELTING_RECIPE) {
            return determineSmeltingRecipeCategory(output);
        } else if (serializer == RecipeSerializer.BLASTING_RECIPE) {
            return determineBlastingRecipeCategory(output);
        } else if (serializer != RecipeSerializer.SMOKING_RECIPE && serializer != RecipeSerializer.CAMPFIRE_COOKING_RECIPE) {
            throw new IllegalStateException("Unknown cooking recipe type");
        } else {
            return CookingBookCategory.FOOD;
        }
    }

    private void ensureValid(ResourceLocation recipeId) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipeId);
        }
    }
}
