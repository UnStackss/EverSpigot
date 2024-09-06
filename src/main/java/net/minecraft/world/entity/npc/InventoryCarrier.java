package net.minecraft.world.entity.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public interface InventoryCarrier {

    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(Mob entity, InventoryCarrier inventoryOwner, ItemEntity item) {
        ItemStack itemstack = item.getItem();

        if (entity.wantsToPickUp(itemstack)) {
            SimpleContainer inventorysubcontainer = inventoryOwner.getInventory();
            boolean flag = inventorysubcontainer.canAddItem(itemstack);

            if (!flag) {
                return;
            }

            // CraftBukkit start
            ItemStack remaining = new SimpleContainer(inventorysubcontainer).addItem(itemstack);
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(entity, item, remaining.getCount(), false).isCancelled()) {
                return;
            }
            // CraftBukkit end

            entity.onItemPickup(item);
            int i = itemstack.getCount();
            ItemStack itemstack1 = inventorysubcontainer.addItem(itemstack);

            entity.take(item, i - itemstack1.getCount());
            if (itemstack1.isEmpty()) {
                item.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            } else {
                itemstack.setCount(itemstack1.getCount());
            }
        }

    }

    default void readInventoryFromTag(CompoundTag nbt, HolderLookup.Provider holderlookup_a) {
        if (nbt.contains("Inventory", 9)) {
            this.getInventory().fromTag(nbt.getList("Inventory", 10), holderlookup_a);
        }

    }

    default void writeInventoryToTag(CompoundTag nbt, HolderLookup.Provider holderlookup_a) {
        nbt.put("Inventory", this.getInventory().createTag(holderlookup_a));
    }
}
