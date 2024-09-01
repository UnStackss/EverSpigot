package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Iterator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.entity.Player;
// CraftBukkit end

public class GrindstoneMenu extends AbstractContainerMenu {

    // CraftBukkit start
    private CraftInventoryView bukkitEntity = null;
    private Player player;

    @Override
    public CraftInventoryView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        CraftInventoryGrindstone inventory = new CraftInventoryGrindstone(this.repairSlots, this.resultSlots);
        this.bukkitEntity = new CraftInventoryView(this.player, inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final Container resultSlots;
    final Container repairSlots;
    private final ContainerLevelAccess access;

    public GrindstoneMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int syncId, Inventory playerInventory, final ContainerLevelAccess context) {
        super(MenuType.GRINDSTONE, syncId);
        this.resultSlots = new ResultContainer(this.createBlockHolder(context)); // Paper - Add missing InventoryHolders
        this.repairSlots = new SimpleContainer(this.createBlockHolder(context), 2) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                GrindstoneMenu.this.slotsChanged(this);
            }

            // CraftBukkit start
            @Override
            public Location getLocation() {
                return context.getLocation();
            }
            // CraftBukkit end
        };
        this.access = context;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) { // CraftBukkit - decompile error
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack);
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) { // CraftBukkit - decompile error
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(stack);
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(net.minecraft.world.entity.player.Player player, ItemStack stack) {
                context.execute((world, blockposition) -> {
                    if (world instanceof ServerLevel) {
                        // Paper start - Fire BlockExpEvent on grindstone use
                        org.bukkit.event.block.BlockExpEvent event = new org.bukkit.event.block.BlockExpEvent(org.bukkit.craftbukkit.block.CraftBlock.at(world, blockposition), this.getExperienceAmount(world));
                        event.callEvent();
                        ExperienceOrb.award((ServerLevel) world, Vec3.atCenterOf(blockposition), event.getExpToDrop(), org.bukkit.entity.ExperienceOrb.SpawnReason.GRINDSTONE, player);
                        // Paper end - Fire BlockExpEvent on grindstone use
                    }

                    world.levelEvent(1042, blockposition, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            private int getExperienceAmount(Level world) {
                int j = 0;

                j += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                j += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (j > 0) {
                    int k = (int) Math.ceil((double) j / 2.0D);

                    return k + world.random.nextInt(k);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(ItemStack stack) {
                int j = 0;
                ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
                Iterator iterator = itemenchantments.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<Holder<Enchantment>> entry = (Entry) iterator.next();
                    Holder<Enchantment> holder = (Holder) entry.getKey();
                    int k = entry.getIntValue();

                    if (!holder.is(EnchantmentTags.CURSE)) {
                        j += ((Enchantment) holder.value()).getMinCost(k);
                    }
                }

                return j;
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

        this.player = (Player) playerInventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.repairSlots) {
            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
        }

    }

    private void createResult() {
        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareGrindstoneEvent(this.getBukkitView(), this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1))); // CraftBukkit
        this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
        this.broadcastChanges();
    }

    private ItemStack computeResult(ItemStack firstInput, ItemStack secondInput) {
        boolean flag = !firstInput.isEmpty() || !secondInput.isEmpty();

        if (!flag) {
            return ItemStack.EMPTY;
        } else if (firstInput.getCount() <= 1 && secondInput.getCount() <= 1) {
            boolean flag1 = !firstInput.isEmpty() && !secondInput.isEmpty();

            if (!flag1) {
                ItemStack itemstack2 = !firstInput.isEmpty() ? firstInput : secondInput;

                return !EnchantmentHelper.hasAnyEnchantments(itemstack2) ? ItemStack.EMPTY : this.removeNonCursesFrom(itemstack2.copy());
            } else {
                return this.mergeItems(firstInput, secondInput);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(ItemStack firstInput, ItemStack secondInput) {
        if (!firstInput.is(secondInput.getItem())) {
            return ItemStack.EMPTY;
        } else {
            int i = Math.max(firstInput.getMaxDamage(), secondInput.getMaxDamage());
            int j = firstInput.getMaxDamage() - firstInput.getDamageValue();
            int k = secondInput.getMaxDamage() - secondInput.getDamageValue();
            int l = j + k + i * 5 / 100;
            byte b0 = 1;

            if (!firstInput.isDamageableItem()) {
                if (firstInput.getMaxStackSize() < 2 || !ItemStack.matches(firstInput, secondInput)) {
                    return ItemStack.EMPTY;
                }

                b0 = 2;
            }

            ItemStack itemstack2 = firstInput.copyWithCount(b0);

            if (itemstack2.isDamageableItem()) {
                itemstack2.set(DataComponents.MAX_DAMAGE, i);
                itemstack2.setDamageValue(Math.max(i - l, 0));
            }

            this.mergeEnchantsFrom(itemstack2, secondInput);
            return this.removeNonCursesFrom(itemstack2);
        }
    }

    private void mergeEnchantsFrom(ItemStack target, ItemStack source) {
        EnchantmentHelper.updateEnchantments(target, (itemenchantments_a) -> {
            ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(source);
            Iterator iterator = itemenchantments.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Holder<Enchantment>> entry = (Entry) iterator.next();
                Holder<Enchantment> holder = (Holder) entry.getKey();

                if (!holder.is(EnchantmentTags.CURSE) || itemenchantments_a.getLevel(holder) == 0) {
                    itemenchantments_a.upgrade(holder, entry.getIntValue());
                }
            }

        });
    }

    private ItemStack removeNonCursesFrom(ItemStack item) {
        ItemEnchantments itemenchantments = EnchantmentHelper.updateEnchantments(item, (itemenchantments_a) -> {
            itemenchantments_a.removeIf((holder) -> {
                return !holder.is(EnchantmentTags.CURSE);
            });
        });

        if (item.is(Items.ENCHANTED_BOOK) && itemenchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }

        int i = 0;

        for (int j = 0; j < itemenchantments.size(); ++j) {
            i = AnvilMenu.calculateIncreasedRepairCost(i);
        }

        item.set(DataComponents.REPAIR_COST, i);
        return item;
    }

    @Override
    public void removed(net.minecraft.world.entity.player.Player player) {
        super.removed(player);
        this.access.execute((world, blockposition) -> {
            this.clearContainer(player, this.repairSlots);
        });
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            ItemStack itemstack2 = this.repairSlots.getItem(0);
            ItemStack itemstack3 = this.repairSlots.getItem(1);

            if (slot == 2) {
                if (!this.moveItemStackTo(itemstack1, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
            } else if (slot != 0 && slot != 1) {
                if (!itemstack2.isEmpty() && !itemstack3.isEmpty()) {
                    if (slot >= 3 && slot < 30) {
                        if (!this.moveItemStackTo(itemstack1, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (slot >= 30 && slot < 39 && !this.moveItemStackTo(itemstack1, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot1.setByPlayer(ItemStack.EMPTY);
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
}
