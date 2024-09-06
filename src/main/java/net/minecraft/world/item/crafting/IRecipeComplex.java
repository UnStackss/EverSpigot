package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import net.minecraft.core.IRegistryCustom;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftComplexRecipe;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public abstract class IRecipeComplex implements RecipeCrafting {

    private final CraftingBookCategory category;

    public IRecipeComplex(CraftingBookCategory craftingbookcategory) {
        this.category = craftingbookcategory;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.a holderlookup_a) {
        return ItemStack.EMPTY;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        CraftItemStack result = CraftItemStack.asCraftMirror(getResultItem(IRegistryCustom.EMPTY));

        CraftComplexRecipe recipe = new CraftComplexRecipe(id, result, this);
        recipe.setGroup(this.getGroup());
        recipe.setCategory(CraftRecipe.getCategory(this.category()));

        return recipe;
    }
    // CraftBukkit end
}
