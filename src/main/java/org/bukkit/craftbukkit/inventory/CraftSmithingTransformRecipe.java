package org.bukkit.craftbukkit.inventory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;

public class CraftSmithingTransformRecipe extends SmithingTransformRecipe implements CraftRecipe {
    public CraftSmithingTransformRecipe(NamespacedKey key, ItemStack result, RecipeChoice template, RecipeChoice base, RecipeChoice addition) {
        super(key, result, template, base, addition);
    }
    // Paper start - Option to prevent data components copy
    public CraftSmithingTransformRecipe(NamespacedKey key, ItemStack result, RecipeChoice template, RecipeChoice base, RecipeChoice addition, boolean copyDataComponents) {
        super(key, result, template, base, addition, copyDataComponents);
    }
    // Paper end - Option to prevent data components copy

    public static CraftSmithingTransformRecipe fromBukkitRecipe(SmithingTransformRecipe recipe) {
        if (recipe instanceof CraftSmithingTransformRecipe) {
            return (CraftSmithingTransformRecipe) recipe;
        }
        CraftSmithingTransformRecipe ret = new CraftSmithingTransformRecipe(recipe.getKey(), recipe.getResult(), recipe.getTemplate(), recipe.getBase(), recipe.getAddition(), recipe.willCopyDataComponents()); // Paper - Option to prevent data components copy
        return ret;
    }

    @Override
    public void addToCraftingManager() {
        ItemStack result = this.getResult();

        MinecraftServer.getServer().getRecipeManager().addRecipe(new RecipeHolder<>(CraftNamespacedKey.toMinecraft(this.getKey()), new net.minecraft.world.item.crafting.SmithingTransformRecipe(this.toNMS(this.getTemplate(), false), this.toNMS(this.getBase(), false), this.toNMS(this.getAddition(), false), CraftItemStack.asNMSCopy(result), this.willCopyDataComponents()))); // Paper - Option to prevent data components copy & support empty RecipeChoice
    }
}
