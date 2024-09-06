package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class CrafterBlockEntity extends RandomizableContainerBlockEntity implements CraftingContainer {

    public static final int CONTAINER_WIDTH = 3;
    public static final int CONTAINER_HEIGHT = 3;
    public static final int CONTAINER_SIZE = 9;
    public static final int SLOT_DISABLED = 1;
    public static final int SLOT_ENABLED = 0;
    public static final int DATA_TRIGGERED = 9;
    public static final int NUM_DATA = 10;
    private NonNullList<ItemStack> items;
    public int craftingTicksRemaining;
    protected final ContainerData containerData;
    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public Location getLocation() {
        if (this.level == null) return null;
        return new org.bukkit.Location(this.level.getWorld(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }
    // CraftBukkit end

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CRAFTER, pos, state);
        this.items = NonNullList.withSize(9, ItemStack.EMPTY);
        this.craftingTicksRemaining = 0;
        this.containerData = new ContainerData() { // CraftBukkit - decompile error
            private final int[] slotStates = new int[9];
            private int triggered = 0;

            @Override
            public int get(int index) {
                return index == 9 ? this.triggered : this.slotStates[index];
            }

            @Override
            public void set(int index, int value) {
                if (index == 9) {
                    this.triggered = value;
                } else {
                    this.slotStates[index] = value;
                }

            }

            @Override
            public int getCount() {
                return 10;
            }
        };
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.crafter");
    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
        return new CrafterMenu(syncId, playerInventory, this, this.containerData);
    }

    public void setSlotState(int slot, boolean enabled) {
        if (this.slotCanBeDisabled(slot)) {
            this.containerData.set(slot, enabled ? 0 : 1);
            this.setChanged();
        }
    }

    public boolean isSlotDisabled(int slot) {
        return slot >= 0 && slot < 9 ? this.containerData.get(slot) == 1 : false;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (this.containerData.get(slot) == 1) {
            return false;
        } else {
            ItemStack itemstack1 = (ItemStack) this.items.get(slot);
            int j = itemstack1.getCount();

            return j >= itemstack1.getMaxStackSize() ? false : (itemstack1.isEmpty() ? true : !this.smallerStackExist(j, itemstack1, slot));
        }
    }

    private boolean smallerStackExist(int count, ItemStack stack, int slot) {
        for (int k = slot + 1; k < 9; ++k) {
            if (!this.isSlotDisabled(k)) {
                ItemStack itemstack1 = this.getItem(k);

                if (itemstack1.isEmpty() || itemstack1.getCount() < count && ItemStack.isSameItemSameComponents(itemstack1, stack)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.craftingTicksRemaining = nbt.getInt("crafting_ticks_remaining");
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbt)) {
            ContainerHelper.loadAllItems(nbt, this.items, registryLookup);
        }

        int[] aint = nbt.getIntArray("disabled_slots");

        for (int i = 0; i < 9; ++i) {
            this.containerData.set(i, 0);
        }

        int[] aint1 = aint;
        int j = aint.length;

        for (int k = 0; k < j; ++k) {
            int l = aint1[k];

            if (this.slotCanBeDisabled(l)) {
                this.containerData.set(l, 1);
            }
        }

        this.containerData.set(9, nbt.getInt("triggered"));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("crafting_ticks_remaining", this.craftingTicksRemaining);
        if (!this.trySaveLootTable(nbt)) {
            ContainerHelper.saveAllItems(nbt, this.items, registryLookup);
        }

        this.addDisabledSlots(nbt);
        this.addTriggered(nbt);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public boolean isEmpty() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (itemstack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        return (ItemStack) this.items.get(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (this.isSlotDisabled(slot)) {
            this.setSlotState(slot, true);
        }

        super.setItem(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> inventory) {
        this.items = inventory;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public void fillStackedContents(StackedContents finder) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            finder.accountSimpleStack(itemstack);
        }

    }

    private void addDisabledSlots(CompoundTag nbt) {
        IntArrayList intarraylist = new IntArrayList();

        for (int i = 0; i < 9; ++i) {
            if (this.isSlotDisabled(i)) {
                intarraylist.add(i);
            }
        }

        nbt.putIntArray("disabled_slots", (List) intarraylist);
    }

    private void addTriggered(CompoundTag nbt) {
        nbt.putInt("triggered", this.containerData.get(9));
    }

    public void setTriggered(boolean triggered) {
        this.containerData.set(9, triggered ? 1 : 0);
    }

    @VisibleForTesting
    public boolean isTriggered() {
        return this.containerData.get(9) == 1;
    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, CrafterBlockEntity blockEntity) {
        int i = blockEntity.craftingTicksRemaining - 1;

        if (i >= 0) {
            blockEntity.craftingTicksRemaining = i;
            if (i == 0) {
                world.setBlock(pos, (BlockState) state.setValue(CrafterBlock.CRAFTING, false), 3);
            }

        }
    }

    public void setCraftingTicksRemaining(int craftingTicksRemaining) {
        this.craftingTicksRemaining = craftingTicksRemaining;
    }

    public int getRedstoneSignal() {
        int i = 0;

        for (int j = 0; j < this.getContainerSize(); ++j) {
            ItemStack itemstack = this.getItem(j);

            if (!itemstack.isEmpty() || this.isSlotDisabled(j)) {
                ++i;
            }
        }

        return i;
    }

    private boolean slotCanBeDisabled(int slot) {
        return slot > -1 && slot < 9 && ((ItemStack) this.items.get(slot)).isEmpty();
    }
}
