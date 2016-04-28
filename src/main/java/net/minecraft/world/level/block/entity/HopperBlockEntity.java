package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
// CraftBukkit end

public class HopperBlockEntity extends RandomizableContainerBlockEntity implements Hopper {

    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    private static final int[][] CACHED_SLOTS = new int[54][];
    private NonNullList<ItemStack> items;
    public int cooldownTime;
    private long tickedGameTime;
    private Direction facing;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    public HopperBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.HOPPER, pos, state);
        this.items = NonNullList.withSize(5, ItemStack.EMPTY);
        this.cooldownTime = -1;
        this.facing = (Direction) state.getValue(HopperBlock.FACING);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(nbt)) {
            ContainerHelper.loadAllItems(nbt, this.items, registryLookup);
        }

        this.cooldownTime = nbt.getInt("TransferCooldown");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if (!this.trySaveLootTable(nbt)) {
            ContainerHelper.saveAllItems(nbt, this.items, registryLookup);
        }

        nbt.putInt("TransferCooldown", this.cooldownTime);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        this.unpackLootTable((Player) null);
        return ContainerHelper.removeItem(this.getItems(), slot, amount);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.unpackLootTable((Player) null);
        this.getItems().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        this.facing = (Direction) state.getValue(HopperBlock.FACING);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.hopper");
    }

    public static void pushItemsTick(Level world, BlockPos pos, BlockState state, HopperBlockEntity blockEntity) {
        --blockEntity.cooldownTime;
        blockEntity.tickedGameTime = world.getGameTime();
        if (!blockEntity.isOnCooldown()) {
            blockEntity.setCooldown(0);
            // Spigot start
            boolean result = HopperBlockEntity.tryMoveItems(world, pos, state, blockEntity, () -> {
                return HopperBlockEntity.suckInItems(world, blockEntity);
            });
            if (!result && blockEntity.level.spigotConfig.hopperCheck > 1) {
                blockEntity.setCooldown(blockEntity.level.spigotConfig.hopperCheck);
            }
            // Spigot end
        }

    }

    // Paper start - Perf: Optimize Hoppers
    private static final int HOPPER_EMPTY = 0;
    private static final int HOPPER_HAS_ITEMS = 1;
    private static final int HOPPER_IS_FULL = 2;

    private static int getFullState(final HopperBlockEntity tileEntity) {
        tileEntity.unpackLootTable(null);

        final List<ItemStack> hopperItems = tileEntity.getItems();

        boolean empty = true;
        boolean full = true;

        for (int i = 0, len = hopperItems.size(); i < len; ++i) {
            final ItemStack stack = hopperItems.get(i);
            if (stack.isEmpty()) {
                full = false;
                continue;
            }

            if (!full) {
                // can't be full
                return HOPPER_HAS_ITEMS;
            }

            empty = false;

            if (stack.getCount() != stack.getMaxStackSize()) {
                // can't be full or empty
                return HOPPER_HAS_ITEMS;
            }
        }

        return empty ? HOPPER_EMPTY : (full ? HOPPER_IS_FULL : HOPPER_HAS_ITEMS);
    }
    // Paper end - Perf: Optimize Hoppers

    private static boolean tryMoveItems(Level world, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, BooleanSupplier booleansupplier) {
        if (world.isClientSide) {
            return false;
        } else {
            if (!blockEntity.isOnCooldown() && (Boolean) state.getValue(HopperBlock.ENABLED)) {
                boolean flag = false;

                final int fullState = getFullState(blockEntity); // Paper - Perf: Optimize Hoppers
                if (fullState != HOPPER_EMPTY) { // Paper - Perf: Optimize Hoppers
                    flag = HopperBlockEntity.ejectItems(world, pos, blockEntity);
                }

                if (fullState != HOPPER_IS_FULL || flag) { // Paper - Perf: Optimize Hoppers
                    flag |= booleansupplier.getAsBoolean();
                }

                if (flag) {
                    blockEntity.setCooldown(world.spigotConfig.hopperTransfer); // Spigot
                    setChanged(world, pos, state);
                    return true;
                }
            }

            return false;
        }
    }

    private boolean inventoryFull() {
        Iterator iterator = this.items.iterator();

        ItemStack itemstack;

        do {
            if (!iterator.hasNext()) {
                return true;
            }

            itemstack = (ItemStack) iterator.next();
        } while (!itemstack.isEmpty() && itemstack.getCount() == itemstack.getMaxStackSize());

        return false;
    }

    // Paper start - Perf: Optimize Hoppers
    private static boolean skipPullModeEventFire;
    private static boolean skipPushModeEventFire;
    public static boolean skipHopperEvents;

    private static boolean hopperPush(final Level level, final Container destination, final Direction direction, final HopperBlockEntity hopper) {
        skipPushModeEventFire = skipHopperEvents;
        boolean foundItem = false;
        for (int i = 0; i < hopper.getContainerSize(); ++i) {
            final ItemStack item = hopper.getItem(i);
            if (!item.isEmpty()) {
                foundItem = true;
                ItemStack origItemStack = item;
                ItemStack movedItem = origItemStack;

                final int originalItemCount = origItemStack.getCount();
                final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
                origItemStack.setCount(movedItemCount);

                // We only need to fire the event once to give protection plugins a chance to cancel this event
                // Because nothing uses getItem, every event call should end up the same result.
                if (!skipPushModeEventFire) {
                    movedItem = callPushMoveEvent(destination, movedItem, hopper);
                    if (movedItem == null) { // cancelled
                        origItemStack.setCount(originalItemCount);
                        return false;
                    }
                }

                final ItemStack remainingItem = addItem(hopper, destination, movedItem, direction);
                final int remainingItemCount = remainingItem.getCount();
                if (remainingItemCount != movedItemCount) {
                    origItemStack = origItemStack.copy(true);
                    origItemStack.setCount(originalItemCount);
                    if (!origItemStack.isEmpty()) {
                        origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
                    }
                    hopper.setItem(i, origItemStack);
                    destination.setChanged();
                    return true;
                }
                origItemStack.setCount(originalItemCount);
            }
        }
        if (foundItem && level.paperConfig().hopper.cooldownWhenFull) { // Inventory was full - cooldown
            hopper.setCooldown(level.spigotConfig.hopperTransfer);
        }
        return false;
    }

    private static boolean hopperPull(final Level level, final Hopper hopper, final Container container, ItemStack origItemStack, final int i) {
        ItemStack movedItem = origItemStack;
        final int originalItemCount = origItemStack.getCount();
        final int movedItemCount = Math.min(level.spigotConfig.hopperAmount, originalItemCount);
        container.setChanged(); // original logic always marks source inv as changed even if no move happens.
        movedItem.setCount(movedItemCount);

        if (!skipPullModeEventFire) {
            movedItem = callPullMoveEvent(hopper, container, movedItem);
            if (movedItem == null) { // cancelled
                origItemStack.setCount(originalItemCount);
                // Drastically improve performance by returning true.
                // No plugin could of relied on the behavior of false as the other call
                // site for IMIE did not exhibit the same behavior
                return true;
            }
        }

        final ItemStack remainingItem = addItem(container, hopper, movedItem, null);
        final int remainingItemCount = remainingItem.getCount();
        if (remainingItemCount != movedItemCount) {
            origItemStack = origItemStack.copy(true);
            origItemStack.setCount(originalItemCount);
            if (!origItemStack.isEmpty()) {
                origItemStack.setCount(originalItemCount - movedItemCount + remainingItemCount);
            }

            ignoreTileUpdates = true;
            container.setItem(i, origItemStack);
            ignoreTileUpdates = false;
            container.setChanged();
            return true;
        }
        origItemStack.setCount(originalItemCount);

        if (level.paperConfig().hopper.cooldownWhenFull) {
            cooldownHopper(hopper);
        }

        return false;
    }

    @Nullable
    private static ItemStack callPushMoveEvent(Container iinventory, ItemStack itemstack, HopperBlockEntity hopper) {
        final Inventory destinationInventory = getInventory(iinventory);
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(hopper.getOwner(false).getInventory(),
            CraftItemStack.asCraftMirror(itemstack), destinationInventory, true);
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            skipPushModeEventFire = true;
        }
        if (!result) {
            cooldownHopper(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemstack;
        }
    }

    @Nullable
    private static ItemStack callPullMoveEvent(final Hopper hopper, final Container container, final ItemStack itemstack) {
        final Inventory sourceInventory = getInventory(container);
        final Inventory destination = getInventory(hopper);

        // Mirror is safe as no plugins ever use this item
        final io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent event = new io.papermc.paper.event.inventory.PaperInventoryMoveItemEvent(sourceInventory, CraftItemStack.asCraftMirror(itemstack), destination, false);
        final boolean result = event.callEvent();
        if (!event.calledGetItem && !event.calledSetItem) {
            skipPullModeEventFire = true;
        }
        if (!result) {
            cooldownHopper(hopper);
            return null;
        }

        if (event.calledSetItem) {
            return CraftItemStack.asNMSCopy(event.getItem());
        } else {
            return itemstack;
        }
    }

    private static Inventory getInventory(final Container container) {
        final Inventory sourceInventory;
        if (container instanceof CompoundContainer compoundContainer) {
            // Have to special-case large chests as they work oddly
            sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest(compoundContainer);
        } else if (container instanceof BlockEntity blockEntity) {
            sourceInventory = blockEntity.getOwner(false).getInventory();
        } else if (container.getOwner() != null) {
            sourceInventory = container.getOwner().getInventory();
        } else {
            sourceInventory = new CraftInventory(container);
        }
        return sourceInventory;
    }

    private static void cooldownHopper(final Hopper hopper) {
        if (hopper instanceof HopperBlockEntity blockEntity && blockEntity.getLevel() != null) {
            blockEntity.setCooldown(blockEntity.getLevel().spigotConfig.hopperTransfer);
        }
    }

    private static boolean allMatch(Container iinventory, Direction enumdirection, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (iinventory instanceof WorldlyContainer) {
            for (int i : ((WorldlyContainer) iinventory).getSlotsForFace(enumdirection)) {
                if (!test.test(iinventory.getItem(i), i)) {
                    return false;
                }
            }
        } else {
            int size = iinventory.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (!test.test(iinventory.getItem(i), i)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean anyMatch(Container iinventory, Direction enumdirection, java.util.function.BiPredicate<ItemStack, Integer> test) {
        if (iinventory instanceof WorldlyContainer) {
            for (int i : ((WorldlyContainer) iinventory).getSlotsForFace(enumdirection)) {
                if (test.test(iinventory.getItem(i), i)) {
                    return true;
                }
            }
        } else {
            int size = iinventory.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (test.test(iinventory.getItem(i), i)) {
                    return true;
                }
            }
        }
        return true;
    }
    private static final java.util.function.BiPredicate<ItemStack, Integer> STACK_SIZE_TEST = (itemstack, i) -> itemstack.getCount() >= itemstack.getMaxStackSize();
    private static final java.util.function.BiPredicate<ItemStack, Integer> IS_EMPTY_TEST = (itemstack, i) -> itemstack.isEmpty();
    // Paper end - Perf: Optimize Hoppers

    private static boolean ejectItems(Level world, BlockPos pos, HopperBlockEntity blockEntity) {
        Container iinventory = HopperBlockEntity.getAttachedContainer(world, pos, blockEntity);

        if (iinventory == null) {
            return false;
        } else {
            Direction enumdirection = blockEntity.facing.getOpposite();

            if (HopperBlockEntity.isFullContainer(iinventory, enumdirection)) {
                return false;
            } else {
                // Paper start - Perf: Optimize Hoppers
                return hopperPush(world, iinventory, enumdirection, blockEntity);
                //for (int i = 0; i < blockEntity.getContainerSize(); ++i) {
                //    ItemStack itemstack = blockEntity.getItem(i);

                //    if (!itemstack.isEmpty()) {
                //        int j = itemstack.getCount();
                //        // CraftBukkit start - Call event when pushing items into other inventories
                //        ItemStack original = itemstack.copy();
                //        CraftItemStack oitemstack = CraftItemStack.asCraftMirror(blockEntity.removeItem(i, world.spigotConfig.hopperAmount)); // Spigot

                //        Inventory destinationInventory;
                //        // Have to special case large chests as they work oddly
                //        if (iinventory instanceof CompoundContainer) {
                //            destinationInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
                //        } else if (iinventory.getOwner() != null) {
                //            destinationInventory = iinventory.getOwner().getInventory();
                //        } else {
                //            destinationInventory = new CraftInventory(iinventory);
                //        }

                //        InventoryMoveItemEvent event = new InventoryMoveItemEvent(tileentityhopper.getOwner().getInventory(), oitemstack, destinationInventory, true);
                //        world.getCraftServer().getPluginManager().callEvent(event);
                //        if (event.isCancelled()) {
                //            blockEntity.setItem(i, original);
                //            blockEntity.setCooldown(world.spigotConfig.hopperTransfer); // Delay hopper checks // Spigot
                //            return false;
                //        }
                //        int origCount = event.getItem().getAmount(); // Spigot
                //        ItemStack itemstack1 = HopperBlockEntity.addItem(blockEntity, iinventory, CraftItemStack.asNMSCopy(event.getItem()), enumdirection);
                //        // CraftBukkit end

                //        if (itemstack1.isEmpty()) {
                //            iinventory.setChanged();
                //            return true;
                //        }

                //        itemstack.setCount(j);
                //        // Spigot start
                //        itemstack.shrink(origCount - itemstack1.getCount());
                //        if (j <= world.spigotConfig.hopperAmount) {
                //          // Spigot end
                //            blockEntity.setItem(i, itemstack);
                //        }
                //    }
                //}

                // return false;
                // Paper end - Perf: Optimize Hoppers
            }
        }
    }

    private static int[] getSlots(Container inventory, Direction side) {
        if (inventory instanceof WorldlyContainer iworldinventory) {
            return iworldinventory.getSlotsForFace(side);
        } else {
            int i = inventory.getContainerSize();

            if (i < HopperBlockEntity.CACHED_SLOTS.length) {
                int[] aint = HopperBlockEntity.CACHED_SLOTS[i];

                if (aint != null) {
                    return aint;
                } else {
                    int[] aint1 = HopperBlockEntity.createFlatSlots(i);

                    HopperBlockEntity.CACHED_SLOTS[i] = aint1;
                    return aint1;
                }
            } else {
                return HopperBlockEntity.createFlatSlots(i);
            }
        }
    }

    private static int[] createFlatSlots(int size) {
        int[] aint = new int[size];

        for (int j = 0; j < aint.length; aint[j] = j++) {
            ;
        }

        return aint;
    }

    private static boolean isFullContainer(Container inventory, Direction direction) {
        int[] aint = HopperBlockEntity.getSlots(inventory, direction);
        int[] aint1 = aint;
        int i = aint.length;

        for (int j = 0; j < i; ++j) {
            int k = aint1[j];
            ItemStack itemstack = inventory.getItem(k);

            if (itemstack.getCount() < itemstack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    public static boolean suckInItems(Level world, Hopper hopper) {
        BlockPos blockposition = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());
        BlockState iblockdata = world.getBlockState(blockposition);
        Container iinventory = HopperBlockEntity.getSourceContainer(world, hopper, blockposition, iblockdata);

        if (iinventory != null) {
            Direction enumdirection = Direction.DOWN;
            skipPullModeEventFire = skipHopperEvents; // Paper - Perf: Optimize Hoppers
            int[] aint = HopperBlockEntity.getSlots(iinventory, enumdirection);
            int i = aint.length;

            for (int j = 0; j < i; ++j) {
                int k = aint[j];

                if (HopperBlockEntity.tryTakeInItemFromSlot(hopper, iinventory, k, enumdirection, world)) { // Spigot
                    return true;
                }
            }

            return false;
        } else {
            boolean flag = hopper.isGridAligned() && iblockdata.isCollisionShapeFullBlock(world, blockposition) && !iblockdata.is(BlockTags.DOES_NOT_BLOCK_HOPPERS);

            if (!flag) {
                Iterator iterator = HopperBlockEntity.getItemsAtAndAbove(world, hopper).iterator();

                while (iterator.hasNext()) {
                    ItemEntity entityitem = (ItemEntity) iterator.next();

                    if (HopperBlockEntity.addItem(hopper, entityitem)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static boolean tryTakeInItemFromSlot(Hopper ihopper, Container iinventory, int i, Direction enumdirection, Level world) { // Spigot
        ItemStack itemstack = iinventory.getItem(i);

        if (!itemstack.isEmpty() && HopperBlockEntity.canTakeItemFromContainer(ihopper, iinventory, itemstack, i, enumdirection)) {
            // Paper start - Perf: Optimize Hoppers
            return hopperPull(world, ihopper, iinventory, itemstack, i);
        //    int j = itemstack.getCount();
        //    // CraftBukkit start - Call event on collection of items from inventories into the hopper
        //    ItemStack original = itemstack.copy();
        //    CraftItemStack oitemstack = CraftItemStack.asCraftMirror(iinventory.removeItem(i, world.spigotConfig.hopperAmount)); // Spigot

        //    Inventory sourceInventory;
        //    // Have to special case large chests as they work oddly
        //    if (iinventory instanceof CompoundContainer) {
        //        sourceInventory = new org.bukkit.craftbukkit.inventory.CraftInventoryDoubleChest((CompoundContainer) iinventory);
        //    } else if (iinventory.getOwner() != null) {
        //        sourceInventory = iinventory.getOwner().getInventory();
        //    } else {
        //        sourceInventory = new CraftInventory(iinventory);
        //    }

        //    InventoryMoveItemEvent event = new InventoryMoveItemEvent(sourceInventory, oitemstack, ihopper.getOwner().getInventory(), false);

        //    Bukkit.getServer().getPluginManager().callEvent(event);
        //    if (event.isCancelled()) {
        //        iinventory.setItem(i, original);

        //        if (ihopper instanceof HopperBlockEntity) {
        //            ((HopperBlockEntity) ihopper).setCooldown(world.spigotConfig.hopperTransfer); // Spigot
        //        }

        //        return false;
        //    }
        //    int origCount = event.getItem().getAmount(); // Spigot
        //    ItemStack itemstack1 = HopperBlockEntity.addItem(iinventory, ihopper, CraftItemStack.asNMSCopy(event.getItem()), null);
        //    // CraftBukkit end

        //    if (itemstack1.isEmpty()) {
        //        iinventory.setChanged();
        //        return true;
        //    }

        //    itemstack.setCount(j);
        //    // Spigot start
        //    itemstack.shrink(origCount - itemstack1.getCount());
        //    if (j <= world.spigotConfig.hopperAmount) {
        //        // Spigot end
        //        iinventory.setItem(i, itemstack);
        //    }
            // Paper end - Perf: Optimize Hoppers
        }

        return false;
    }

    public static boolean addItem(Container inventory, ItemEntity itemEntity) {
        boolean flag = false;
        // CraftBukkit start
        if (InventoryPickupItemEvent.getHandlerList().getRegisteredListeners().length > 0) { // Paper - optimize hoppers
        InventoryPickupItemEvent event = new InventoryPickupItemEvent(getInventory(inventory), (org.bukkit.entity.Item) itemEntity.getBukkitEntity()); // Paper - Perf: Optimize Hoppers; use getInventory() to avoid snapshot creation
        itemEntity.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        // CraftBukkit end
        } // Paper - Perf: Optimize Hoppers
        ItemStack itemstack = itemEntity.getItem().copy();
        ItemStack itemstack1 = HopperBlockEntity.addItem((Container) null, inventory, itemstack, (Direction) null);

        if (itemstack1.isEmpty()) {
            flag = true;
            itemEntity.setItem(ItemStack.EMPTY);
            itemEntity.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
        } else {
            itemEntity.setItem(itemstack1);
        }

        return flag;
    }

    public static ItemStack addItem(@Nullable Container from, Container to, ItemStack stack, @Nullable Direction side) {
        int i;

        if (to instanceof WorldlyContainer iworldinventory) {
            if (side != null) {
                int[] aint = iworldinventory.getSlotsForFace(side);

                for (i = 0; i < aint.length && !stack.isEmpty(); ++i) {
                    stack = HopperBlockEntity.tryMoveInItem(from, to, stack, aint[i], side);
                }

                return stack;
            }
        }

        int j = to.getContainerSize();

        for (i = 0; i < j && !stack.isEmpty(); ++i) {
            stack = HopperBlockEntity.tryMoveInItem(from, to, stack, i, side);
        }

        return stack;
    }

    private static boolean canPlaceItemInContainer(Container inventory, ItemStack stack, int slot, @Nullable Direction side) {
        if (!inventory.canPlaceItem(slot, stack)) {
            return false;
        } else {
            boolean flag;

            if (inventory instanceof WorldlyContainer) {
                WorldlyContainer iworldinventory = (WorldlyContainer) inventory;

                if (!iworldinventory.canPlaceItemThroughFace(slot, stack, side)) {
                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }
    }

    private static boolean canTakeItemFromContainer(Container hopperInventory, Container fromInventory, ItemStack stack, int slot, Direction facing) {
        if (!fromInventory.canTakeItem(hopperInventory, slot, stack)) {
            return false;
        } else {
            boolean flag;

            if (fromInventory instanceof WorldlyContainer) {
                WorldlyContainer iworldinventory = (WorldlyContainer) fromInventory;

                if (!iworldinventory.canTakeItemThroughFace(slot, stack, facing)) {
                    flag = false;
                    return flag;
                }
            }

            flag = true;
            return flag;
        }
    }

    private static ItemStack tryMoveInItem(@Nullable Container from, Container to, ItemStack stack, int slot, @Nullable Direction side) {
        ItemStack itemstack1 = to.getItem(slot);

        if (HopperBlockEntity.canPlaceItemInContainer(to, stack, slot, side)) {
            boolean flag = false;
            boolean flag1 = to.isEmpty();

            if (itemstack1.isEmpty()) {
                // Spigot start - SPIGOT-6693, InventorySubcontainer#setItem
                ItemStack leftover = ItemStack.EMPTY; // Paper - Make hoppers respect inventory max stack size
                if (!stack.isEmpty() && stack.getCount() > to.getMaxStackSize()) {
                    leftover = stack; // Paper - Make hoppers respect inventory max stack size
                    stack = stack.split(to.getMaxStackSize());
                }
                // Spigot end
                ignoreTileUpdates = true; // Paper - Perf: Optimize Hoppers
                to.setItem(slot, stack);
                ignoreTileUpdates = false; // Paper - Perf: Optimize Hoppers
                stack = leftover; // Paper - Make hoppers respect inventory max stack size
                flag = true;
            } else if (HopperBlockEntity.canMergeItems(itemstack1, stack)) {
                int j = Math.min(stack.getMaxStackSize(), to.getMaxStackSize()) - itemstack1.getCount(); // Paper - Make hoppers respect inventory max stack size
                int k = Math.min(stack.getCount(), j);

                stack.shrink(k);
                itemstack1.grow(k);
                flag = k > 0;
            }

            if (flag) {
                if (flag1 && to instanceof HopperBlockEntity) {
                    HopperBlockEntity tileentityhopper = (HopperBlockEntity) to;

                    if (!tileentityhopper.isOnCustomCooldown()) {
                        byte b0 = 0;

                        if (from instanceof HopperBlockEntity) {
                            HopperBlockEntity tileentityhopper1 = (HopperBlockEntity) from;

                            if (tileentityhopper.tickedGameTime >= tileentityhopper1.tickedGameTime) {
                                b0 = 1;
                            }
                        }

                        tileentityhopper.setCooldown(tileentityhopper.level.spigotConfig.hopperTransfer - b0); // Spigot
                    }
                }

                to.setChanged();
            }
        }

        return stack;
    }

    // CraftBukkit start
    @Nullable
    private static Container runHopperInventorySearchEvent(Container inventory, CraftBlock hopper, CraftBlock searchLocation, HopperInventorySearchEvent.ContainerType containerType) {
        HopperInventorySearchEvent event = new HopperInventorySearchEvent((inventory != null) ? new CraftInventory(inventory) : null, containerType, hopper, searchLocation);
        Bukkit.getServer().getPluginManager().callEvent(event);
        CraftInventory craftInventory = (CraftInventory) event.getInventory();
        return (craftInventory != null) ? craftInventory.getInventory() : null;
    }
    // CraftBukkit end

    @Nullable
    private static Container getAttachedContainer(Level world, BlockPos pos, HopperBlockEntity blockEntity) {
        // CraftBukkit start
        BlockPos searchPosition = pos.relative(blockEntity.facing);
        Container inventory = HopperBlockEntity.getContainerAt(world, searchPosition);

        CraftBlock hopper = CraftBlock.at(world, pos);
        CraftBlock searchBlock = CraftBlock.at(world, searchPosition);
        return HopperBlockEntity.runHopperInventorySearchEvent(inventory, hopper, searchBlock, HopperInventorySearchEvent.ContainerType.DESTINATION);
        // CraftBukkit end
    }

    @Nullable
    private static Container getSourceContainer(Level world, Hopper hopper, BlockPos pos, BlockState state) {
        // CraftBukkit start
        Container inventory = HopperBlockEntity.getContainerAt(world, pos, state, hopper.getLevelX(), hopper.getLevelY() + 1.0D, hopper.getLevelZ());

        BlockPos blockPosition = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY(), hopper.getLevelZ());
        CraftBlock hopper1 = CraftBlock.at(world, blockPosition);
        CraftBlock container = CraftBlock.at(world, blockPosition.above());
        return HopperBlockEntity.runHopperInventorySearchEvent(inventory, hopper1, container, HopperInventorySearchEvent.ContainerType.SOURCE);
        // CraftBukkit end
    }

    public static List<ItemEntity> getItemsAtAndAbove(Level world, Hopper hopper) {
        AABB axisalignedbb = hopper.getSuckAabb().move(hopper.getLevelX() - 0.5D, hopper.getLevelY() - 0.5D, hopper.getLevelZ() - 0.5D);

        return world.getEntitiesOfClass(ItemEntity.class, axisalignedbb, EntitySelector.ENTITY_STILL_ALIVE);
    }

    @Nullable
    public static Container getContainerAt(Level world, BlockPos pos) {
        return HopperBlockEntity.getContainerAt(world, pos, world.getBlockState(pos), (double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, true);
    }

    @Nullable
    private static Container getContainerAt(Level world, BlockPos pos, BlockState state, double x, double y, double z) {
        // Paper start - Perf: Optimize Hoppers
        return HopperBlockEntity.getContainerAt(world, pos, state, x, y, z, false);
    }
    @Nullable
    private static Container getContainerAt(Level world, BlockPos pos, BlockState state, double x, double y, double z, boolean optimizeEntities) {
        // Paper end - Perf: Optimize Hoppers
        Container iinventory = HopperBlockEntity.getBlockContainer(world, pos, state);

        if (iinventory == null && (!optimizeEntities || !world.paperConfig().hopper.ignoreOccludingBlocks || !state.getBukkitMaterial().isOccluding())) { // Paper - Perf: Optimize Hoppers
            iinventory = HopperBlockEntity.getEntityContainer(world, x, y, z);
        }

        return iinventory;
    }

    @Nullable
    private static Container getBlockContainer(Level world, BlockPos pos, BlockState state) {
        if ( !world.spigotConfig.hopperCanLoadChunks && !world.hasChunkAt( pos ) ) return null; // Spigot
        Block block = state.getBlock();

        if (block instanceof WorldlyContainerHolder) {
            return ((WorldlyContainerHolder) block).getContainer(state, world, pos);
        } else {
            if (state.hasBlockEntity()) {
                BlockEntity tileentity = world.getBlockEntity(pos);

                if (tileentity instanceof Container) {
                    Container iinventory = (Container) tileentity;

                    if (iinventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
                        iinventory = ChestBlock.getContainer((ChestBlock) block, state, world, pos, true);
                    }

                    return iinventory;
                }
            }

            return null;
        }
    }

    @Nullable
    private static Container getEntityContainer(Level world, double x, double y, double z) {
        List<Entity> list = world.getEntitiesOfClass((Class) Container.class, new AABB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D), EntitySelector.CONTAINER_ENTITY_SELECTOR); // Paper - Perf: Optimize hoppers

        return !list.isEmpty() ? (Container) list.get(world.random.nextInt(list.size())) : null;
    }

    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() < first.getMaxStackSize() && ItemStack.isSameItemSameComponents(first, second); // Paper - Perf: Optimize Hoppers; used to return true for full itemstacks?!
    }

    @Override
    public double getLevelX() {
        return (double) this.worldPosition.getX() + 0.5D;
    }

    @Override
    public double getLevelY() {
        return (double) this.worldPosition.getY() + 0.5D;
    }

    @Override
    public double getLevelZ() {
        return (double) this.worldPosition.getZ() + 0.5D;
    }

    @Override
    public boolean isGridAligned() {
        return true;
    }

    public void setCooldown(int transferCooldown) {
        this.cooldownTime = transferCooldown;
    }

    private boolean isOnCooldown() {
        return this.cooldownTime > 0;
    }

    private boolean isOnCustomCooldown() {
        return this.cooldownTime > 8;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> inventory) {
        this.items = inventory;
    }

    public static void entityInside(Level world, BlockPos pos, BlockState state, Entity entity, HopperBlockEntity blockEntity) {
        if (entity instanceof ItemEntity entityitem) {
            if (!entityitem.getItem().isEmpty() && entity.getBoundingBox().move((double) (-pos.getX()), (double) (-pos.getY()), (double) (-pos.getZ())).intersects(blockEntity.getSuckAabb())) {
                HopperBlockEntity.tryMoveItems(world, pos, state, blockEntity, () -> {
                    return HopperBlockEntity.addItem(blockEntity, entityitem);
                });
            }
        }

    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory playerInventory) {
        return new HopperMenu(syncId, playerInventory, this);
    }
}
