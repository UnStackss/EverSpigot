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
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;

public class ShapelessRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final Item result;
    private final int count;
    private final NonNullList<Ingredient> ingredients = NonNullList.create();
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    @Nullable
    private String group;

    public ShapelessRecipeBuilder(RecipeCategory category, ItemLike output, int count) {
        this.category = category;
        this.result = output.asItem();
        this.count = count;
    }

    public static ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemLike output) {
        return new ShapelessRecipeBuilder(category, output, 1);
    }

    public static ShapelessRecipeBuilder shapeless(RecipeCategory category, ItemLike output, int count) {
        return new ShapelessRecipeBuilder(category, output, count);
    }

    public ShapelessRecipeBuilder requires(TagKey<Item> tag) {
        return this.requires(Ingredient.of(tag));
    }

    public ShapelessRecipeBuilder requires(ItemLike itemProvider) {
        return this.requires(itemProvider, 1);
    }

    public ShapelessRecipeBuilder requires(ItemLike itemProvider, int size) {
        for (int i = 0; i < size; i++) {
            this.requires(Ingredient.of(itemProvider));
        }

        return this;
    }

    public ShapelessRecipeBuilder requires(Ingredient ingredient) {
        return this.requires(ingredient, 1);
    }

    public ShapelessRecipeBuilder requires(Ingredient ingredient, int size) {
        for (int i = 0; i < size; i++) {
            this.ingredients.add(ingredient);
        }

        return this;
    }

    @Override
    public ShapelessRecipeBuilder unlockedBy(String string, Criterion<?> criterion) {
        this.criteria.put(string, criterion);
        return this;
    }

    @Override
    public ShapelessRecipeBuilder group(@Nullable String string) {
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
        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(
            Objects.requireNonNullElse(this.group, ""),
            RecipeBuilder.determineBookCategory(this.category),
            new ItemStack(this.result, this.count),
            this.ingredients
        );
        exporter.accept(recipeId, shapelessRecipe, builder.build(recipeId.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceLocation recipeId) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipeId);
        }
    }
}
