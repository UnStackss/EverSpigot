package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

public class SmithingTrimRecipeBuilder {
    private final RecipeCategory category;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();

    public SmithingTrimRecipeBuilder(RecipeCategory category, Ingredient template, Ingredient base, Ingredient addition) {
        this.category = category;
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    public static SmithingTrimRecipeBuilder smithingTrim(Ingredient template, Ingredient base, Ingredient addition, RecipeCategory category) {
        return new SmithingTrimRecipeBuilder(category, template, base, addition);
    }

    public SmithingTrimRecipeBuilder unlocks(String name, Criterion<?> criterion) {
        this.criteria.put(name, criterion);
        return this;
    }

    public void save(RecipeOutput exporter, ResourceLocation recipeId) {
        this.ensureValid(recipeId);
        Advancement.Builder builder = exporter.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeId))
            .rewards(AdvancementRewards.Builder.recipe(recipeId))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        SmithingTrimRecipe smithingTrimRecipe = new SmithingTrimRecipe(this.template, this.base, this.addition);
        exporter.accept(recipeId, smithingTrimRecipe, builder.build(recipeId.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private void ensureValid(ResourceLocation recipeId) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipeId);
        }
    }
}
