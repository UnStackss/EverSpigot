package net.minecraft.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryCrafter;
import org.bukkit.craftbukkit.inventory.view.CraftCrafterView;
// CraftBukkit end

public class CrafterMenu extends AbstractContainerMenu implements ContainerListener {

    // CraftBukkit start
    private CraftCrafterView bukkitEntity = null;

    @Override
    public CraftCrafterView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventoryCrafter inventory = new CraftInventoryCrafter(this.container, this.resultContainer);
        this.bukkitEntity = new CraftCrafterView(this.player.getBukkitEntity(), inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
    protected static final int SLOT_COUNT = 9;
    private static final int INV_SLOT_START = 9;
    private static final int INV_SLOT_END = 36;
    private static final int USE_ROW_SLOT_START = 36;
    private static final int USE_ROW_SLOT_END = 45;
    private final ResultContainer resultContainer = new ResultContainer();
    private final ContainerData containerData;
    private final Player player;
    private final CraftingContainer container;

    public CrafterMenu(int syncId, Inventory playerInventory) {
        super(MenuType.CRAFTER_3x3, syncId);
        this.player = playerInventory.player;
        this.containerData = new SimpleContainerData(10);
        this.container = new TransientCraftingContainer(this, 3, 3);
        this.addSlots(playerInventory);
    }

    public CrafterMenu(int syncId, Inventory playerInventory, CraftingContainer inputInventory, ContainerData propertyDelegate) {
        super(MenuType.CRAFTER_3x3, syncId);
        this.player = playerInventory.player;
        this.containerData = propertyDelegate;
        this.container = inputInventory;
        checkContainerSize(inputInventory, 9);
        inputInventory.startOpen(playerInventory.player);
        this.addSlots(playerInventory);
        this.addSlotListener(this);
    }

    private void addSlots(Inventory playerInventory) {
        int i;
        int j;

        for (j = 0; j < 3; ++j) {
            for (i = 0; i < 3; ++i) {
                int k = i + j * 3;

                this.addSlot(new CrafterSlot(this.container, k, 26 + i * 18, 17 + j * 18, this));
            }
        }

        for (j = 0; j < 3; ++j) {
            for (i = 0; i < 9; ++i) {
                this.addSlot(new Slot(playerInventory, i + j * 9 + 9, 8 + i * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 142));
        }

        this.addSlot(new NonInteractiveResultSlot(this.resultContainer, 0, 134, 35));
        this.addDataSlots(this.containerData);
        this.refreshRecipeResult();
    }

    public void setSlotState(int slot, boolean enabled) {
        CrafterSlot crafterslot = (CrafterSlot) this.getSlot(slot);

        this.containerData.set(crafterslot.index, enabled ? 0 : 1);
        this.broadcastChanges();
    }

    public boolean isSlotDisabled(int slot) {
        return slot > -1 && slot < 9 ? this.containerData.get(slot) == 1 : false;
    }

    public boolean isPowered() {
        return this.containerData.get(9) == 1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            if (slot < 9) {
                if (!this.moveItemStackTo(itemstack1, 9, 45, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 9, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot1.set(ItemStack.EMPTY);
            } else {
                slot1.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot1.onTake(player, itemstack1);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.container.stillValid(player);
    }

    private void refreshRecipeResult() {
        Player entityhuman = this.player;

        if (entityhuman instanceof ServerPlayer entityplayer) {
            Level world = entityplayer.level();
            CraftingInput craftinginput = this.container.asCraftInput();
            ItemStack itemstack = (ItemStack) CrafterBlock.getPotentialResults(world, craftinginput).map((recipeholder) -> {
                return ((CraftingRecipe) recipeholder.value()).assemble(craftinginput, world.registryAccess());
            }).orElse(ItemStack.EMPTY);

            this.resultContainer.setItem(0, itemstack);
        }

    }

    public Container getContainer() {
        return this.container;
    }

    @Override
    public void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack) {
        this.refreshRecipeResult();
    }

    @Override
    public void dataChanged(AbstractContainerMenu handler, int property, int value) {}
}
