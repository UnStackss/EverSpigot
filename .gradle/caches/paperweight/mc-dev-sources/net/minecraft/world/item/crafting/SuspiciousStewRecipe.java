package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;

public class SuspiciousStewRecipe extends CustomRecipe {
    public SuspiciousStewRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        boolean bl = false;
        boolean bl2 = false;
        boolean bl3 = false;
        boolean bl4 = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack = input.getItem(i);
            if (!itemStack.isEmpty()) {
                if (itemStack.is(Blocks.BROWN_MUSHROOM.asItem()) && !bl3) {
                    bl3 = true;
                } else if (itemStack.is(Blocks.RED_MUSHROOM.asItem()) && !bl2) {
                    bl2 = true;
                } else if (itemStack.is(ItemTags.SMALL_FLOWERS) && !bl) {
                    bl = true;
                } else {
                    if (!itemStack.is(Items.BOWL) || bl4) {
                        return false;
                    }

                    bl4 = true;
                }
            }
        }

        return bl && bl3 && bl2 && bl4;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        ItemStack itemStack = new ItemStack(Items.SUSPICIOUS_STEW, 1);

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack2 = input.getItem(i);
            if (!itemStack2.isEmpty()) {
                SuspiciousEffectHolder suspiciousEffectHolder = SuspiciousEffectHolder.tryGet(itemStack2.getItem());
                if (suspiciousEffectHolder != null) {
                    itemStack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, suspiciousEffectHolder.getSuspiciousEffects());
                    break;
                }
            }
        }

        return itemStack;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 2 && height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.SUSPICIOUS_STEW;
    }
}
