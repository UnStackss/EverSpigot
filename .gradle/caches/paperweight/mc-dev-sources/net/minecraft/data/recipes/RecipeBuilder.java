package net.minecraft.data.recipes;

import javax.annotation.Nullable;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.ItemLike;

public interface RecipeBuilder {
    ResourceLocation ROOT_RECIPE_ADVANCEMENT = ResourceLocation.withDefaultNamespace("recipes/root");

    RecipeBuilder unlockedBy(String name, Criterion<?> criterion);

    RecipeBuilder group(@Nullable String group);

    Item getResult();

    void save(RecipeOutput exporter, ResourceLocation recipeId);

    default void save(RecipeOutput exporter) {
        this.save(exporter, getDefaultRecipeId(this.getResult()));
    }

    default void save(RecipeOutput exporter, String recipePath) {
        ResourceLocation resourceLocation = getDefaultRecipeId(this.getResult());
        ResourceLocation resourceLocation2 = ResourceLocation.parse(recipePath);
        if (resourceLocation2.equals(resourceLocation)) {
            throw new IllegalStateException("Recipe " + recipePath + " should remove its 'save' argument as it is equal to default one");
        } else {
            this.save(exporter, resourceLocation2);
        }
    }

    static ResourceLocation getDefaultRecipeId(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem());
    }

    static CraftingBookCategory determineBookCategory(RecipeCategory category) {
        return switch (category) {
            case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
            case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
    }
}
