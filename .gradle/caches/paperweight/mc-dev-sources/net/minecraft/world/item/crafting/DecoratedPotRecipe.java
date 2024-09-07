package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;

public class DecoratedPotRecipe extends CustomRecipe {
    public DecoratedPotRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        if (!this.canCraftInDimensions(input.width(), input.height())) {
            return false;
        } else {
            for (int i = 0; i < input.size(); i++) {
                ItemStack itemStack = input.getItem(i);
                switch (i) {
                    case 1:
                    case 3:
                    case 5:
                    case 7:
                        if (!itemStack.is(ItemTags.DECORATED_POT_INGREDIENTS)) {
                            return false;
                        }
                        break;
                    case 2:
                    case 4:
                    case 6:
                    default:
                        if (!itemStack.is(Items.AIR)) {
                            return false;
                        }
                }
            }

            return true;
        }
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        PotDecorations potDecorations = new PotDecorations(
            input.getItem(1).getItem(), input.getItem(3).getItem(), input.getItem(5).getItem(), input.getItem(7).getItem()
        );
        return DecoratedPotBlockEntity.createDecoratedPotItem(potDecorations);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width == 3 && height == 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.DECORATED_POT_RECIPE;
    }
}
