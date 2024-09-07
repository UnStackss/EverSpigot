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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.ItemLike;

public class SingleItemRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final Item result;
    private final Ingredient ingredient;
    private final int count;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    @Nullable
    private String group;
    private final SingleItemRecipe.Factory<?> factory;

    public SingleItemRecipeBuilder(RecipeCategory category, SingleItemRecipe.Factory<?> recipeFactory, Ingredient input, ItemLike output, int count) {
        this.category = category;
        this.factory = recipeFactory;
        this.result = output.asItem();
        this.ingredient = input;
        this.count = count;
    }

    public static SingleItemRecipeBuilder stonecutting(Ingredient input, RecipeCategory category, ItemLike output) {
        return new SingleItemRecipeBuilder(category, StonecutterRecipe::new, input, output, 1);
    }

    public static SingleItemRecipeBuilder stonecutting(Ingredient input, RecipeCategory category, ItemLike output, int count) {
        return new SingleItemRecipeBuilder(category, StonecutterRecipe::new, input, output, count);
    }

    @Override
    public SingleItemRecipeBuilder unlockedBy(String string, Criterion<?> criterion) {
        this.criteria.put(string, criterion);
        return this;
    }

    @Override
    public SingleItemRecipeBuilder group(@Nullable String string) {
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
        SingleItemRecipe singleItemRecipe = this.factory
            .create(Objects.requireNonNullElse(this.group, ""), this.ingredient, new ItemStack(this.result, this.count));
        exporter.accept(recipeId, singleItemRecipe, builder.build(recipeId.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceLocation recipeId) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipeId);
        }
    }
}
