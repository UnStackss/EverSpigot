package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public class ContainerHelper {
    public static final String TAG_ITEMS = "Items";

    public static ItemStack removeItem(List<ItemStack> stacks, int slot, int amount) {
        return slot >= 0 && slot < stacks.size() && !stacks.get(slot).isEmpty() && amount > 0 ? stacks.get(slot).split(amount) : ItemStack.EMPTY;
    }

    public static ItemStack takeItem(List<ItemStack> stacks, int slot) {
        return slot >= 0 && slot < stacks.size() ? stacks.set(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static CompoundTag saveAllItems(CompoundTag nbt, NonNullList<ItemStack> stacks, HolderLookup.Provider registries) {
        return saveAllItems(nbt, stacks, true, registries);
    }

    public static CompoundTag saveAllItems(CompoundTag nbt, NonNullList<ItemStack> stacks, boolean setIfEmpty, HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack itemStack = stacks.get(i);
            if (!itemStack.isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)i);
                listTag.add(itemStack.save(registries, compoundTag));
            }
        }

        if (!listTag.isEmpty() || setIfEmpty) {
            nbt.put("Items", listTag);
        }

        return nbt;
    }

    public static void loadAllItems(CompoundTag nbt, NonNullList<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag listTag = nbt.getList("Items", 10);

        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag compoundTag = listTag.getCompound(i);
            int j = compoundTag.getByte("Slot") & 255;
            if (j >= 0 && j < stacks.size()) {
                stacks.set(j, ItemStack.parse(registries, compoundTag).orElse(ItemStack.EMPTY));
            }
        }
    }

    public static int clearOrCountMatchingItems(Container inventory, Predicate<ItemStack> shouldRemove, int maxCount, boolean dryRun) {
        int i = 0;

        for (int j = 0; j < inventory.getContainerSize(); j++) {
            ItemStack itemStack = inventory.getItem(j);
            int k = clearOrCountMatchingItems(itemStack, shouldRemove, maxCount - i, dryRun);
            if (k > 0 && !dryRun && itemStack.isEmpty()) {
                inventory.setItem(j, ItemStack.EMPTY);
            }

            i += k;
        }

        return i;
    }

    public static int clearOrCountMatchingItems(ItemStack stack, Predicate<ItemStack> shouldRemove, int maxCount, boolean dryRun) {
        if (stack.isEmpty() || !shouldRemove.test(stack)) {
            return 0;
        } else if (dryRun) {
            return stack.getCount();
        } else {
            int i = maxCount < 0 ? stack.getCount() : Math.min(maxCount, stack.getCount());
            stack.shrink(i);
            return i;
        }
    }
}
