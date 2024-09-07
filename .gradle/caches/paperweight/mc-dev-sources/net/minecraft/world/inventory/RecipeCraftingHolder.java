package net.minecraft.world.inventory;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public interface RecipeCraftingHolder {
    void setRecipeUsed(@Nullable RecipeHolder<?> recipe);

    @Nullable
    RecipeHolder<?> getRecipeUsed();

    default void awardUsedRecipes(Player player, List<ItemStack> ingredients) {
        RecipeHolder<?> recipeHolder = this.getRecipeUsed();
        if (recipeHolder != null) {
            player.triggerRecipeCrafted(recipeHolder, ingredients);
            if (!recipeHolder.value().isSpecial()) {
                player.awardRecipes(Collections.singleton(recipeHolder));
                this.setRecipeUsed(null);
            }
        }
    }

    default boolean setRecipeUsed(Level world, ServerPlayer player, RecipeHolder<?> recipe) {
        if (!recipe.value().isSpecial() && world.getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING) && !player.getRecipeBook().contains(recipe)) {
            return false;
        } else {
            this.setRecipeUsed(recipe);
            return true;
        }
    }
}
