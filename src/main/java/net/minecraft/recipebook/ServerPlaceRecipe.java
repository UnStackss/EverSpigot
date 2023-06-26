package net.minecraft.recipebook;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public class ServerPlaceRecipe<I extends RecipeInput, R extends Recipe<I>> implements PlaceRecipe<Integer> {
    private static final int ITEM_NOT_FOUND = -1;
    protected final StackedContents stackedContents = new StackedContents();
    protected Inventory inventory;
    protected RecipeBookMenu<I, R> menu;

    public ServerPlaceRecipe(RecipeBookMenu<I, R> handler) {
        this.menu = handler;
    }

    public void recipeClicked(ServerPlayer entity, @Nullable RecipeHolder<R> recipe, boolean craftAll) {
        if (recipe != null && entity.getRecipeBook().contains(recipe)) {
            this.inventory = entity.getInventory();
            if (this.testClearGrid() || entity.isCreative()) {
                this.stackedContents.clear();
                this.stackedContents.initializeExtras(recipe.value(), null); // Paper - Improve exact choice recipe ingredients
                entity.getInventory().fillStackedContents(this.stackedContents);
                this.menu.fillCraftSlotsStackedContents(this.stackedContents);
                if (this.stackedContents.canCraft(recipe.value(), null)) {
                    this.handleRecipeClicked(recipe, craftAll);
                } else {
                    this.clearGrid();
                    entity.connection.send(new ClientboundPlaceGhostRecipePacket(entity.containerMenu.containerId, recipe));
                }

                entity.getInventory().setChanged();
            }
        }
    }

    protected void clearGrid() {
        for (int i = 0; i < this.menu.getSize(); i++) {
            if (this.menu.shouldMoveToInventory(i)) {
                ItemStack itemStack = this.menu.getSlot(i).getItem().copy();
                this.inventory.placeItemBackInInventory(itemStack, false);
                this.menu.getSlot(i).set(itemStack);
            }
        }

        this.menu.clearCraftingContent();
    }

    protected void handleRecipeClicked(RecipeHolder<R> recipe, boolean craftAll) {
        boolean bl = this.menu.recipeMatches(recipe);
        int i = this.stackedContents.getBiggestCraftableStack(recipe, null);
        if (bl) {
            for (int j = 0; j < this.menu.getGridHeight() * this.menu.getGridWidth() + 1; j++) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemStack = this.menu.getSlot(j).getItem();
                    if (!itemStack.isEmpty() && Math.min(i, itemStack.getMaxStackSize()) < itemStack.getCount() + 1) {
                        return;
                    }
                }
            }
        }

        int k = this.getStackSize(craftAll, i, bl);
        IntList intList = new IntArrayList();
        if (this.stackedContents.canCraft(recipe.value(), intList, k)) {
            int l = k;

            for (int m : intList) {
                ItemStack itemStack2 = StackedContents.fromStackingIndexWithExtras(m, this.stackedContents); // Paper - Improve exact choice recipe ingredients
                if (!itemStack2.isEmpty()) {
                    int n = itemStack2.getMaxStackSize();
                    if (n < l) {
                        l = n;
                    }
                }
            }

            if (this.stackedContents.canCraft(recipe.value(), intList, l)) {
                this.clearGrid();
                this.placeRecipe(this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), recipe, intList.iterator(), l);
            }
        }
    }

    @Override
    public void addItemToSlot(Integer input, int slot, int amount, int gridX, int gridY) {
        Slot slot2 = this.menu.getSlot(slot);
        // Paper start - Improve exact choice recipe ingredients
        ItemStack itemStack = null;
        boolean isExact = false;
        if (this.stackedContents.extrasMap != null && input >= net.minecraft.core.registries.BuiltInRegistries.ITEM.size()) {
            itemStack = StackedContents.fromStackingIndexExtras(input, this.stackedContents.extrasMap).copy();
            isExact = true;
        }
        if (itemStack == null) {
            itemStack = StackedContents.fromStackingIndex(input);
        }
        // Paper end - Improve exact choice recipe ingredients
        if (!itemStack.isEmpty()) {
            int i = amount;

            while (i > 0) {
                i = this.moveItemToGrid(slot2, itemStack, i, isExact); // Paper - Improve exact choice recipe ingredients
                if (i == -1) {
                    return;
                }
            }
        }
    }

    protected int getStackSize(boolean craftAll, int limit, boolean recipeInCraftingSlots) {
        int i = 1;
        if (craftAll) {
            i = limit;
        } else if (recipeInCraftingSlots) {
            i = Integer.MAX_VALUE;

            for (int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; j++) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemStack = this.menu.getSlot(j).getItem();
                    if (!itemStack.isEmpty() && i > itemStack.getCount()) {
                        i = itemStack.getCount();
                    }
                }
            }

            if (i != Integer.MAX_VALUE) {
                i++;
            }
        }

        return i;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper - Improve exact choice recipe ingredients

    protected int moveItemToGrid(Slot slot, ItemStack stack, int i) {
        // Paper start - Improve exact choice recipe ingredients
        return this.moveItemToGrid(slot, stack, i, false);
    }
    protected int moveItemToGrid(Slot slot, ItemStack stack, int i, final boolean isExact) {
        int j = isExact ? this.inventory.findSlotMatchingItem(stack) : this.inventory.findSlotMatchingUnusedItem(stack);
        // Paper end - Improve exact choice recipe ingredients
        if (j == -1) {
            return -1;
        } else {
            ItemStack itemStack = this.inventory.getItem(j);
            int k;
            if (i < itemStack.getCount()) {
                this.inventory.removeItem(j, i);
                k = i;
            } else {
                this.inventory.removeItemNoUpdate(j);
                k = itemStack.getCount();
            }

            if (slot.getItem().isEmpty()) {
                slot.set(itemStack.copyWithCount(k));
            } else {
                slot.getItem().grow(k);
            }

            return i - k;
        }
    }

    private boolean testClearGrid() {
        List<ItemStack> list = Lists.newArrayList();
        int i = this.getAmountOfFreeSlotsInInventory();

        for (int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; j++) {
            if (j != this.menu.getResultSlotIndex()) {
                ItemStack itemStack = this.menu.getSlot(j).getItem().copy();
                if (!itemStack.isEmpty()) {
                    int k = this.inventory.getSlotWithRemainingSpace(itemStack);
                    if (k == -1 && list.size() <= i) {
                        for (ItemStack itemStack2 : list) {
                            if (ItemStack.isSameItem(itemStack2, itemStack)
                                && itemStack2.getCount() != itemStack2.getMaxStackSize()
                                && itemStack2.getCount() + itemStack.getCount() <= itemStack2.getMaxStackSize()) {
                                itemStack2.grow(itemStack.getCount());
                                itemStack.setCount(0);
                                break;
                            }
                        }

                        if (!itemStack.isEmpty()) {
                            if (list.size() >= i) {
                                return false;
                            }

                            list.add(itemStack);
                        }
                    } else if (k == -1) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int getAmountOfFreeSlotsInInventory() {
        int i = 0;

        for (ItemStack itemStack : this.inventory.items) {
            if (itemStack.isEmpty()) {
                i++;
            }
        }

        return i;
    }
}
