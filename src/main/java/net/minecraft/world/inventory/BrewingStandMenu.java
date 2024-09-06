package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryBrewer;
import org.bukkit.craftbukkit.inventory.view.CraftBrewingStandView;
// CraftBukkit end

public class BrewingStandMenu extends AbstractContainerMenu {

    private static final int BOTTLE_SLOT_START = 0;
    private static final int BOTTLE_SLOT_END = 2;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int SLOT_COUNT = 5;
    private static final int DATA_COUNT = 2;
    private static final int INV_SLOT_START = 5;
    private static final int INV_SLOT_END = 32;
    private static final int USE_ROW_SLOT_START = 32;
    private static final int USE_ROW_SLOT_END = 41;
    private final Container brewingStand;
    private final ContainerData brewingStandData;
    private final Slot ingredientSlot;

    // CraftBukkit start
    private CraftBrewingStandView bukkitEntity = null;
    private Inventory player;
    // CraftBukkit end

    public BrewingStandMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new SimpleContainer(5), new SimpleContainerData(2));
    }

    public BrewingStandMenu(int syncId, Inventory playerInventory, Container inventory, ContainerData propertyDelegate) {
        super(MenuType.BREWING_STAND, syncId);
        this.player = playerInventory; // CraftBukkit
        checkContainerSize(inventory, 5);
        checkContainerDataCount(propertyDelegate, 2);
        this.brewingStand = inventory;
        this.brewingStandData = propertyDelegate;
        PotionBrewing potionbrewer = playerInventory.player.level().potionBrewing();

        this.addSlot(new BrewingStandMenu.PotionSlot(inventory, 0, 56, 51));
        this.addSlot(new BrewingStandMenu.PotionSlot(inventory, 1, 79, 58));
        this.addSlot(new BrewingStandMenu.PotionSlot(inventory, 2, 102, 51));
        this.ingredientSlot = this.addSlot(new BrewingStandMenu.IngredientsSlot(potionbrewer, inventory, 3, 79, 17));
        this.addSlot(new BrewingStandMenu.FuelSlot(inventory, 4, 17, 17));
        this.addDataSlots(propertyDelegate);

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 142));
        }

    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return this.brewingStand.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            if ((slot < 0 || slot > 2) && slot != 3 && slot != 4) {
                if (BrewingStandMenu.FuelSlot.mayPlaceItem(itemstack)) {
                    if (this.moveItemStackTo(itemstack1, 4, 5, false) || this.ingredientSlot.mayPlace(itemstack1) && !this.moveItemStackTo(itemstack1, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (this.ingredientSlot.mayPlace(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, 3, 4, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (BrewingStandMenu.PotionSlot.mayPlaceItem(itemstack)) {
                    if (!this.moveItemStackTo(itemstack1, 0, 3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slot >= 5 && slot < 32) {
                    if (!this.moveItemStackTo(itemstack1, 32, 41, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slot >= 32 && slot < 41) {
                    if (!this.moveItemStackTo(itemstack1, 5, 32, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 5, 41, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemstack1, 5, 41, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
            }

            if (itemstack1.isEmpty()) {
                slot1.setByPlayer(ItemStack.EMPTY);
            } else {
                slot1.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot1.onTake(player, itemstack);
        }

        return itemstack;
    }

    public int getFuel() {
        return this.brewingStandData.get(1);
    }

    public int getBrewingTicks() {
        return this.brewingStandData.get(0);
    }

    private static class PotionSlot extends Slot {

        public PotionSlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PotionSlot.mayPlaceItem(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            Optional<Holder<Potion>> optional = ((PotionContents) stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)).potion();

            if (optional.isPresent() && player instanceof ServerPlayer entityplayer) {
                CriteriaTriggers.BREWED_POTION.trigger(entityplayer, (Holder) optional.get());
            }

            super.onTake(player, stack);
        }

        public static boolean mayPlaceItem(ItemStack stack) {
            return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE);
        }
    }

    private static class IngredientsSlot extends Slot {

        private final PotionBrewing potionBrewing;

        public IngredientsSlot(PotionBrewing brewingRecipeRegistry, Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
            this.potionBrewing = brewingRecipeRegistry;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.potionBrewing.isIngredient(stack);
        }
    }

    private static class FuelSlot extends Slot {

        public FuelSlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return FuelSlot.mayPlaceItem(stack);
        }

        public static boolean mayPlaceItem(ItemStack stack) {
            return stack.is(Items.BLAZE_POWDER);
        }
    }

    // CraftBukkit start
    @Override
    public CraftBrewingStandView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventoryBrewer inventory = new CraftInventoryBrewer(this.brewingStand);
        this.bukkitEntity = new CraftBrewingStandView(this.player.player.getBukkitEntity(), inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
}
