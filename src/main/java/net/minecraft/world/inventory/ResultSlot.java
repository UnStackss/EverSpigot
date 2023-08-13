package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

public class ResultSlot extends Slot {
    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ResultSlot(Player player, CraftingContainer input, Container inventory, int index, int x, int y) {
        super(inventory, index, x, y);
        this.player = player;
        this.craftSlots = input;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void onSwapCraft(int amount) {
        this.removeCount += amount;
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        if (this.removeCount > 0) {
            stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
        }

        if (this.container instanceof RecipeCraftingHolder recipeCraftingHolder) {
            recipeCraftingHolder.awardUsedRecipes(this.player, this.craftSlots.getItems());
        }

        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        this.checkTakeAchievements(stack);
        CraftingInput.Positioned positioned = this.craftSlots.asPositionedCraftInput();
        CraftingInput craftingInput = positioned.input();
        int i = positioned.left();
        int j = positioned.top();
        NonNullList<ItemStack> nonNullList = player.level().getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, craftingInput, player.level(), this.craftSlots.getCurrentRecipe()); // Paper - Perf: Improve mass crafting; check last recipe used first

        for (int k = 0; k < craftingInput.height(); k++) {
            for (int l = 0; l < craftingInput.width(); l++) {
                int m = l + i + (k + j) * this.craftSlots.getWidth();
                ItemStack itemStack = this.craftSlots.getItem(m);
                ItemStack itemStack2 = nonNullList.get(l + k * craftingInput.width());
                if (!itemStack.isEmpty()) {
                    this.craftSlots.removeItem(m, 1);
                    itemStack = this.craftSlots.getItem(m);
                }

                if (!itemStack2.isEmpty()) {
                    if (itemStack.isEmpty()) {
                        this.craftSlots.setItem(m, itemStack2);
                    } else if (ItemStack.isSameItemSameComponents(itemStack, itemStack2)) {
                        itemStack2.grow(itemStack.getCount());
                        this.craftSlots.setItem(m, itemStack2);
                    } else if (!this.player.getInventory().add(itemStack2)) {
                        this.player.drop(itemStack2, false);
                    }
                }
            }
        }
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
