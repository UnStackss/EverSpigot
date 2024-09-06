package net.minecraft.world.inventory;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftInventoryStonecutter;
import org.bukkit.craftbukkit.inventory.view.CraftStonecutterView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class StonecutterMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END = 29;
    private static final int USE_ROW_SLOT_START = 29;
    private static final int USE_ROW_SLOT_END = 38;
    private final ContainerLevelAccess access;
    private final DataSlot selectedRecipeIndex;
    private final Level level;
    private List<RecipeHolder<StonecutterRecipe>> recipes;
    private ItemStack input;
    long lastSoundTime;
    final Slot inputSlot;
    final Slot resultSlot;
    Runnable slotUpdateListener;
    public final Container container;
    final ResultContainer resultContainer;
    // CraftBukkit start
    private CraftStonecutterView bukkitEntity = null;
    private Player player;

    @Override
    public CraftStonecutterView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventoryStonecutter inventory = new CraftInventoryStonecutter(this.container, this.resultContainer);
        this.bukkitEntity = new CraftStonecutterView(this.player, inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end

    public StonecutterMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL);
    }

    public StonecutterMenu(int syncId, Inventory playerInventory, final ContainerLevelAccess context) {
        super(MenuType.STONECUTTER, syncId);
        this.selectedRecipeIndex = DataSlot.standalone();
        this.recipes = Lists.newArrayList();
        this.input = ItemStack.EMPTY;
        this.slotUpdateListener = () -> {
        };
        this.container = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                StonecutterMenu.this.slotsChanged(this);
                StonecutterMenu.this.slotUpdateListener.run();
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return context.getLocation();
            }
            // CraftBukkit end
        };
        this.resultContainer = new ResultContainer();
        this.access = context;
        this.level = playerInventory.player.level();
        this.inputSlot = this.addSlot(new Slot(this.container, 0, 20, 33));
        this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(net.minecraft.world.entity.player.Player player, ItemStack stack) {
                stack.onCraftedBy(player.level(), player, stack.getCount());
                StonecutterMenu.this.resultContainer.awardUsedRecipes(player, this.getRelevantItems());
                ItemStack itemstack1 = StonecutterMenu.this.inputSlot.remove(1);

                if (!itemstack1.isEmpty()) {
                    StonecutterMenu.this.setupResultSlot();
                }

                context.execute((world, blockposition) -> {
                    long j = world.getGameTime();

                    if (StonecutterMenu.this.lastSoundTime != j) {
                        world.playSound((net.minecraft.world.entity.player.Player) null, blockposition, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        StonecutterMenu.this.lastSoundTime = j;
                    }

                });
                super.onTake(player, stack);
            }

            private List<ItemStack> getRelevantItems() {
                return List.of(StonecutterMenu.this.inputSlot.getItem());
            }
        });

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + j * 9 + 9, 8 + k * 18, 84 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 142));
        }

        this.addDataSlot(this.selectedRecipeIndex);
        this.player = (Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    public int getSelectedRecipeIndex() {
        return this.selectedRecipeIndex.get();
    }

    public List<RecipeHolder<StonecutterRecipe>> getRecipes() {
        return this.recipes;
    }

    public int getNumRecipes() {
        return this.recipes.size();
    }

    public boolean hasInputItem() {
        return this.inputSlot.hasItem() && !this.recipes.isEmpty();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.STONECUTTER);
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int id) {
        if (this.isValidRecipeIndex(id)) {
            this.selectedRecipeIndex.set(id);
            this.setupResultSlot();
        }

        return true;
    }

    private boolean isValidRecipeIndex(int id) {
        return id >= 0 && id < this.recipes.size();
    }

    @Override
    public void slotsChanged(Container inventory) {
        ItemStack itemstack = this.inputSlot.getItem();

        if (!itemstack.is(this.input.getItem())) {
            this.input = itemstack.copy();
            this.setupRecipeList(inventory, itemstack);
        }

    }

    private static SingleRecipeInput createRecipeInput(Container inventory) {
        return new SingleRecipeInput(inventory.getItem(0));
    }

    private void setupRecipeList(Container input, ItemStack stack) {
        this.recipes.clear();
        this.selectedRecipeIndex.set(-1);
        this.resultSlot.set(ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            this.recipes = this.level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, StonecutterMenu.createRecipeInput(input), this.level);
        }

    }

    void setupResultSlot() {
        if (!this.recipes.isEmpty() && this.isValidRecipeIndex(this.selectedRecipeIndex.get())) {
            RecipeHolder<StonecutterRecipe> recipeholder = (RecipeHolder) this.recipes.get(this.selectedRecipeIndex.get());
            ItemStack itemstack = ((StonecutterRecipe) recipeholder.value()).assemble(StonecutterMenu.createRecipeInput(this.container), this.level.registryAccess());

            if (itemstack.isItemEnabled(this.level.enabledFeatures())) {
                this.resultContainer.setRecipeUsed(recipeholder);
                this.resultSlot.set(itemstack);
            } else {
                this.resultSlot.set(ItemStack.EMPTY);
            }
        } else {
            this.resultSlot.set(ItemStack.EMPTY);
        }

        this.broadcastChanges();
    }

    @Override
    public MenuType<?> getType() {
        return MenuType.STONECUTTER;
    }

    public void registerUpdateListener(Runnable contentsChangedListener) {
        this.slotUpdateListener = contentsChangedListener;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();
            Item item = itemstack1.getItem();

            itemstack = itemstack1.copy();
            if (slot == 1) {
                item.onCraftedBy(itemstack1, player.level(), player);
                if (!this.moveItemStackTo(itemstack1, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
            } else if (slot == 0) {
                if (!this.moveItemStackTo(itemstack1, 2, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.level.getRecipeManager().getRecipeFor(RecipeType.STONECUTTING, new SingleRecipeInput(itemstack1), this.level).isPresent()) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot >= 2 && slot < 29) {
                if (!this.moveItemStackTo(itemstack1, 29, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot >= 29 && slot < 38 && !this.moveItemStackTo(itemstack1, 2, 29, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot1.setByPlayer(ItemStack.EMPTY);
            }

            slot1.setChanged();
            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot1.onTake(player, itemstack1);
            this.broadcastChanges();
        }

        return itemstack;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(1);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(player, this.container);
        });
    }
}
