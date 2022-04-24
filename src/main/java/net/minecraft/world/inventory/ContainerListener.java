package net.minecraft.world.inventory;

import net.minecraft.world.item.ItemStack;

public interface ContainerListener {
    void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack);

    // Paper start - Add PlayerInventorySlotChangeEvent
    default void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack oldStack, ItemStack stack) {
        slotChanged(handler, slotId, stack);
    }
    // Paper end - Add PlayerInventorySlotChangeEvent

    void dataChanged(AbstractContainerMenu handler, int property, int value);
}
