package net.minecraft.world.inventory;

import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public abstract class RecipeBookMenu<I extends RecipeInput, R extends Recipe<I>> extends AbstractContainerMenu {
    public RecipeBookMenu(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    public void handlePlacement(boolean craftAll, RecipeHolder<?> recipe, ServerPlayer player) {
        RecipeHolder<R> recipeHolder = (RecipeHolder<R>)recipe;
        this.beginPlacingRecipe();

        try {
            new ServerPlaceRecipe<>(this).recipeClicked(player, recipeHolder, craftAll);
        } finally {
            this.finishPlacingRecipe((RecipeHolder<R>)recipe);
        }
    }

    protected void beginPlacingRecipe() {
    }

    protected void finishPlacingRecipe(RecipeHolder<R> recipe) {
    }

    public abstract void fillCraftSlotsStackedContents(StackedContents finder);

    public abstract void clearCraftingContent();

    public abstract boolean recipeMatches(RecipeHolder<R> recipe);

    public abstract int getResultSlotIndex();

    public abstract int getGridWidth();

    public abstract int getGridHeight();

    public abstract int getSize();

    public abstract RecipeBookType getRecipeBookType();

    public abstract boolean shouldMoveToInventory(int index);
}
