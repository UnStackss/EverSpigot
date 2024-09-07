package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class BookCloningRecipe extends CustomRecipe {
    public BookCloningRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        int i = 0;
        ItemStack itemStack = ItemStack.EMPTY;

        for (int j = 0; j < input.size(); j++) {
            ItemStack itemStack2 = input.getItem(j);
            if (!itemStack2.isEmpty()) {
                if (itemStack2.is(Items.WRITTEN_BOOK)) {
                    if (!itemStack.isEmpty()) {
                        return false;
                    }

                    itemStack = itemStack2;
                } else {
                    if (!itemStack2.is(Items.WRITABLE_BOOK)) {
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
                if (itemStack2.is(Items.WRITTEN_BOOK)) {
                    if (!itemStack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }

                    itemStack = itemStack2;
                } else {
                    if (!itemStack2.is(Items.WRITABLE_BOOK)) {
                        return ItemStack.EMPTY;
                    }

                    i++;
                }
            }
        }

        WrittenBookContent writtenBookContent = itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (!itemStack.isEmpty() && i >= 1 && writtenBookContent != null) {
            WrittenBookContent writtenBookContent2 = writtenBookContent.tryCraftCopy();
            if (writtenBookContent2 == null) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemStack3 = itemStack.copyWithCount(i);
                itemStack3.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent2);
                return itemStack3;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> nonNullList = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < nonNullList.size(); i++) {
            ItemStack itemStack = input.getItem(i);
            if (itemStack.getItem().hasCraftingRemainingItem()) {
                nonNullList.set(i, new ItemStack(itemStack.getItem().getCraftingRemainingItem()));
            } else if (itemStack.getItem() instanceof WrittenBookItem) {
                nonNullList.set(i, itemStack.copyWithCount(1));
                break;
            }
        }

        return nonNullList;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.BOOK_CLONING;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }
}
