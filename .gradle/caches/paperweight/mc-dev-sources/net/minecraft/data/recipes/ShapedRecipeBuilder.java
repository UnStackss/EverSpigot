package net.minecraft.data.recipes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;

public class ShapedRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final Item result;
    private final int count;
    private final List<String> rows = Lists.newArrayList();
    private final Map<Character, Ingredient> key = Maps.newLinkedHashMap();
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    @Nullable
    private String group;
    private boolean showNotification = true;

    public ShapedRecipeBuilder(RecipeCategory category, ItemLike output, int count) {
        this.category = category;
        this.result = output.asItem();
        this.count = count;
    }

    public static ShapedRecipeBuilder shaped(RecipeCategory category, ItemLike output) {
        return shaped(category, output, 1);
    }

    public static ShapedRecipeBuilder shaped(RecipeCategory category, ItemLike output, int count) {
        return new ShapedRecipeBuilder(category, output, count);
    }

    public ShapedRecipeBuilder define(Character c, TagKey<Item> tag) {
        return this.define(c, Ingredient.of(tag));
    }

    public ShapedRecipeBuilder define(Character c, ItemLike itemProvider) {
        return this.define(c, Ingredient.of(itemProvider));
    }

    public ShapedRecipeBuilder define(Character c, Ingredient ingredient) {
        if (this.key.containsKey(c)) {
            throw new IllegalArgumentException("Symbol '" + c + "' is already defined!");
        } else if (c == ' ') {
            throw new IllegalArgumentException("Symbol ' ' (whitespace) is reserved and cannot be defined");
        } else {
            this.key.put(c, ingredient);
            return this;
        }
    }

    public ShapedRecipeBuilder pattern(String patternStr) {
        if (!this.rows.isEmpty() && patternStr.length() != this.rows.get(0).length()) {
            throw new IllegalArgumentException("Pattern must be the same width on every line!");
        } else {
            this.rows.add(patternStr);
            return this;
        }
    }

    @Override
    public ShapedRecipeBuilder unlockedBy(String string, Criterion<?> criterion) {
        this.criteria.put(string, criterion);
        return this;
    }

    @Override
    public ShapedRecipeBuilder group(@Nullable String string) {
        this.group = string;
        return this;
    }

    public ShapedRecipeBuilder showNotification(boolean showNotification) {
        this.showNotification = showNotification;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result;
    }

    @Override
    public void save(RecipeOutput exporter, ResourceLocation recipeId) {
        ShapedRecipePattern shapedRecipePattern = this.ensureValid(recipeId);
        Advancement.Builder builder = exporter.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeId))
            .rewards(AdvancementRewards.Builder.recipe(recipeId))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(builder::addCriterion);
        ShapedRecipe shapedRecipe = new ShapedRecipe(
            Objects.requireNonNullElse(this.group, ""),
            RecipeBuilder.determineBookCategory(this.category),
            shapedRecipePattern,
            new ItemStack(this.result, this.count),
            this.showNotification
        );
        exporter.accept(recipeId, shapedRecipe, builder.build(recipeId.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private ShapedRecipePattern ensureValid(ResourceLocation recipeId) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + recipeId);
        } else {
            return ShapedRecipePattern.of(this.key, this.rows);
        }
    }
}
