package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
// CraftBukkit start
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public class BrewingStandBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int[] SLOTS_FOR_UP = new int[]{3};
    private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
    private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
    public static final int FUEL_USES = 20;
    public static final int DATA_BREW_TIME = 0;
    public static final int DATA_FUEL_USES = 1;
    public static final int NUM_DATA_VALUES = 2;
    private NonNullList<ItemStack> items;
    public int brewTime;
    private boolean[] lastPotionCount;
    private Item ingredient;
    public int fuel;
    protected final ContainerData dataAccess;
    // CraftBukkit start - add fields and methods
    private int lastTick = MinecraftServer.currentTick;
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end

    public BrewingStandBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BREWING_STAND, pos, state);
        this.items = NonNullList.withSize(5, ItemStack.EMPTY);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int index) {
                int j;

                switch (index) {
                    case 0:
                        j = BrewingStandBlockEntity.this.brewTime;
                        break;
                    case 1:
                        j = BrewingStandBlockEntity.this.fuel;
                        break;
                    default:
                        j = 0;
                }

                return j;
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0:
                        BrewingStandBlockEntity.this.brewTime = value;
                        break;
                    case 1:
                        BrewingStandBlockEntity.this.fuel = value;
                }

            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.brewing");
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> inventory) {
        this.items = inventory;
    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, BrewingStandBlockEntity blockEntity) {
        ItemStack itemstack = (ItemStack) blockEntity.items.get(4);

        if (blockEntity.fuel <= 0 && itemstack.is(Items.BLAZE_POWDER)) {
            // CraftBukkit start
            BrewingStandFuelEvent event = new BrewingStandFuelEvent(CraftBlock.at(world, pos), CraftItemStack.asCraftMirror(itemstack), 20);
            world.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }

            blockEntity.fuel = event.getFuelPower();
            if (blockEntity.fuel > 0 && event.isConsuming()) {
                itemstack.shrink(1);
            }
            // CraftBukkit end
            setChanged(world, pos, state);
        }

        boolean flag = BrewingStandBlockEntity.isBrewable(world.potionBrewing(), blockEntity.items);
        boolean flag1 = blockEntity.brewTime > 0;
        ItemStack itemstack1 = (ItemStack) blockEntity.items.get(3);

        // CraftBukkit start - Use wall time instead of ticks for brewing
        int elapsedTicks = MinecraftServer.currentTick - blockEntity.lastTick;
        blockEntity.lastTick = MinecraftServer.currentTick;

        if (flag1) {
            blockEntity.brewTime -= elapsedTicks;
            boolean flag2 = blockEntity.brewTime <= 0; // == -> <=
            // CraftBukkit end

            if (flag2 && flag) {
                BrewingStandBlockEntity.doBrew(world, pos, blockEntity.items, blockEntity); // CraftBukkit
            } else if (!flag || !itemstack1.is(blockEntity.ingredient)) {
                blockEntity.brewTime = 0;
            }

            setChanged(world, pos, state);
        } else if (flag && blockEntity.fuel > 0) {
            --blockEntity.fuel;
            // CraftBukkit start
            BrewingStartEvent event = new BrewingStartEvent(CraftBlock.at(world, pos), CraftItemStack.asCraftMirror(itemstack1), 400);
            world.getCraftServer().getPluginManager().callEvent(event);
            blockEntity.brewTime = event.getTotalBrewTime(); // 400 -> event.getTotalBrewTime()
            // CraftBukkit end
            blockEntity.ingredient = itemstack1.getItem();
            setChanged(world, pos, state);
        }

        boolean[] aboolean = blockEntity.getPotionBits();

        if (!Arrays.equals(aboolean, blockEntity.lastPotionCount)) {
            blockEntity.lastPotionCount = aboolean;
            BlockState iblockdata1 = state;

            if (!(state.getBlock() instanceof BrewingStandBlock)) {
                return;
            }

            for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; ++i) {
                iblockdata1 = (BlockState) iblockdata1.setValue(BrewingStandBlock.HAS_BOTTLE[i], aboolean[i]);
            }

            world.setBlock(pos, iblockdata1, 2);
        }

    }

    private boolean[] getPotionBits() {
        boolean[] aboolean = new boolean[3];

        for (int i = 0; i < 3; ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty()) {
                aboolean[i] = true;
            }
        }

        return aboolean;
    }

    private static boolean isBrewable(PotionBrewing brewingRecipeRegistry, NonNullList<ItemStack> slots) {
        ItemStack itemstack = (ItemStack) slots.get(3);

        if (itemstack.isEmpty()) {
            return false;
        } else if (!brewingRecipeRegistry.isIngredient(itemstack)) {
            return false;
        } else {
            for (int i = 0; i < 3; ++i) {
                ItemStack itemstack1 = (ItemStack) slots.get(i);

                if (!itemstack1.isEmpty() && brewingRecipeRegistry.hasMix(itemstack1, itemstack)) {
                    return true;
                }
            }

            return false;
        }
    }

    private static void doBrew(Level world, BlockPos blockposition, NonNullList<ItemStack> nonnulllist, BrewingStandBlockEntity tileentitybrewingstand) { // CraftBukkit
        ItemStack itemstack = (ItemStack) nonnulllist.get(3);
        PotionBrewing potionbrewer = world.potionBrewing();

        // CraftBukkit start
        InventoryHolder owner = tileentitybrewingstand.getOwner();
        List<org.bukkit.inventory.ItemStack> brewResults = new ArrayList<>(3);
        for (int i = 0; i < 3; ++i) {
            brewResults.add(i, CraftItemStack.asCraftMirror(potionbrewer.mix(itemstack, (ItemStack) nonnulllist.get(i))));
        }

        if (owner != null) {
            BrewEvent event = new BrewEvent(CraftBlock.at(world, blockposition), (org.bukkit.inventory.BrewerInventory) owner.getInventory(), brewResults, tileentitybrewingstand.fuel);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end

        for (int i = 0; i < 3; ++i) {
            // CraftBukkit start - validate index in case it is cleared by plugins
            if (i < brewResults.size()) {
                nonnulllist.set(i, CraftItemStack.asNMSCopy(brewResults.get(i)));
            } else {
                nonnulllist.set(i, ItemStack.EMPTY);
            }
            // CraftBukkit end
        }

        itemstack.shrink(1);
        if (itemstack.getItem().hasCraftingRemainingItem()) {
            ItemStack itemstack1 = new ItemStack(itemstack.getItem().getCraftingRemainingItem());

            if (itemstack.isEmpty()) {
                itemstack = itemstack1;
            } else {
                Containers.dropItemStack(world, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), itemstack1);
            }
        }

        nonnulllist.set(3, itemstack);
        world.levelEvent(1035, blockposition, 0);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.items, registryLookup);
        this.brewTime = nbt.getShort("BrewTime");
        if (this.brewTime > 0) {
            this.ingredient = ((ItemStack) this.items.get(3)).getItem();
        }

        this.fuel = nbt.getByte("Fuel");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putShort("BrewTime", (short) this.brewTime);
        ContainerHelper.saveAllItems(nbt, this.items, registryLookup);
        nbt.putByte("Fuel", (byte) this.fuel);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        PotionBrewing potionbrewer = this.level != null ? this.level.potionBrewing() : PotionBrewing.EMPTY; // Paper - move up
        if (slot == 3) {

            return potionbrewer.isIngredient(stack);
        } else {
            return slot == 4 ? stack.is(Items.BLAZE_POWDER) : (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE) || potionbrewer.isCustomInput(stack)) && this.getItem(slot).isEmpty(); // Paper - Custom Potion Mixes
        }
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? BrewingStandBlockEntity.SLOTS_FOR_UP : (side == Direction.DOWN ? BrewingStandBlockEntity.SLOTS_FOR_DOWN : BrewingStandBlockEntity.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return slot == 3 ? stack.is(Items.GLASS_BOTTLE) : true;
    }

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
        return new BrewingStandMenu(syncId, playerInventory, this, this.dataAccess);
    }
}
