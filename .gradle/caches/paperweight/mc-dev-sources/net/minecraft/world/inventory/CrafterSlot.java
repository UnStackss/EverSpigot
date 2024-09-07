package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class CrafterSlot extends Slot {
    private final CrafterMenu menu;

    public CrafterSlot(Container inventory, int index, int x, int y, CrafterMenu crafterScreenHandler) {
        super(inventory, index, x, y);
        this.menu = crafterScreenHandler;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !this.menu.isSlotDisabled(this.index) && super.mayPlace(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.menu.slotsChanged(this.container);
    }
}
