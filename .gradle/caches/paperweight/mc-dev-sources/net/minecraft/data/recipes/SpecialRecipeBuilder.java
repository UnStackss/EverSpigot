package net.minecraft.data.recipes;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;

public class SpecialRecipeBuilder {
    private final Function<CraftingBookCategory, Recipe<?>> factory;

    public SpecialRecipeBuilder(Function<CraftingBookCategory, Recipe<?>> recipeFactory) {
        this.factory = recipeFactory;
    }

    public static SpecialRecipeBuilder special(Function<CraftingBookCategory, Recipe<?>> recipeFactory) {
        return new SpecialRecipeBuilder(recipeFactory);
    }

    public void save(RecipeOutput exporter, String id) {
        this.save(exporter, ResourceLocation.parse(id));
    }

    public void save(RecipeOutput exporter, ResourceLocation id) {
        exporter.accept(id, this.factory.apply(CraftingBookCategory.MISC), null);
    }
}
