package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class MapCloningRecipe extends CustomRecipe {
    public MapCloningRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        int i = 0;
        ItemStack itemStack = ItemStack.EMPTY;

        for (int j = 0; j < input.size(); j++) {
            ItemStack itemStack2 = input.getItem(j);
            if (!itemStack2.isEmpty()) {
                if (itemStack2.is(Items.FILLED_MAP)) {
                    if (!itemStack.isEmpty()) {
                        return false;
                    }

                    itemStack = itemStack2;
                } else {
                    if (!itemStack2.is(Items.MAP)) {
                        return false;
                    }

                    i++;
                }
            }
        }

        return !itemStack.isEmpty() && i > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        int i = 0;
        ItemStack itemStack = ItemStack.EMPTY;

        for (int j = 0; j < input.size(); j++) {
            ItemStack itemStack2 = input.getItem(j);
            if (!itemStack2.isEmpty()) {
                if (itemStack2.is(Items.FILLED_MAP)) {
                    if (!itemStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemStack = itemStack2;
                } else {
                    if (!itemStack2.is(Items.MAP)) {
                        return ItemStack.EMPTY;
                    }

                    i++;
                }
            }
        }

        return !itemStack.isEmpty() && i >= 1 ? itemStack.copyWithCount(i + 1) : ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.MAP_CLONING;
    }
}
