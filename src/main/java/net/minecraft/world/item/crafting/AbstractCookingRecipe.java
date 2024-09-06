package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class AbstractCookingRecipe implements Recipe<SingleRecipeInput> {
    protected final RecipeType<?> type;
    protected final CookingBookCategory category;
    protected final String group;
    protected final Ingredient ingredient;
    protected final ItemStack result;
    protected final float experience;
    protected final int cookingTime;

    public AbstractCookingRecipe(
        RecipeType<?> type, String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime
    ) {
        this.type = type;
        this.category = category;
        this.group = group;
        this.ingredient = ingredient;
        this.result = result;
        this.experience = experience;
        this.cookingTime = cookingTime;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level world) {
        return this.ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider lookup) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> nonNullList = NonNullList.create();
        nonNullList.add(this.ingredient);
        return nonNullList;
    }

    public float getExperience() {
        return this.experience;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registriesLookup) {
        return this.result;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    public int getCookingTime() {
        return this.cookingTime;
    }

    @Override
    public RecipeType<?> getType() {
        return this.type;
    }

    public CookingBookCategory category() {
        return this.category;
    }

    public interface Factory<T extends AbstractCookingRecipe> {
        T create(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime);
    }
}
