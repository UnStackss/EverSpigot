package net.minecraft.world.inventory;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.view.CraftAnvilView;
// CraftBukkit end

public class AnvilMenu extends ItemCombinerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    @Nullable
    public String itemName;
    public final DataSlot cost;
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;
    // CraftBukkit start
    public static final int DEFAULT_DENIED_COST = -1;
    public int maximumRepairCost = 40;
    private CraftAnvilView bukkitEntity;
    // CraftBukkit end

    public AnvilMenu(int syncId, Inventory inventory) {
        this(syncId, inventory, ContainerLevelAccess.NULL);
    }

    public AnvilMenu(int syncId, Inventory inventory, ContainerLevelAccess context) {
        super(MenuType.ANVIL, syncId, inventory, context);
        this.cost = DataSlot.standalone();
        this.addDataSlot(this.cost);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, (itemstack) -> {
            return true;
        }).withSlot(1, 76, 47, (itemstack) -> {
            return true;
        }).withResultSlot(2, 134, 47).build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(BlockTags.ANVIL);
    }

    @Override
    protected boolean mayPickup(Player player, boolean present) {
        return (player.hasInfiniteMaterials() || player.experienceLevel >= this.cost.get()) && this.cost.get() > AnvilMenu.DEFAULT_DENIED_COST && present; // CraftBukkit - allow cost 0 like a free item
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-this.cost.get());
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        if (this.repairItemCountCost > 0) {
            ItemStack itemstack1 = this.inputSlots.getItem(1);

            if (!itemstack1.isEmpty() && itemstack1.getCount() > this.repairItemCountCost) {
                itemstack1.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack1);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        this.access.execute((world, blockposition) -> {
            BlockState iblockdata = world.getBlockState(blockposition);

            if (!player.hasInfiniteMaterials() && iblockdata.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
                BlockState iblockdata1 = AnvilBlock.damage(iblockdata);

                // Paper start - AnvilDamageEvent
                com.destroystokyo.paper.event.block.AnvilDamagedEvent event = new com.destroystokyo.paper.event.block.AnvilDamagedEvent(getBukkitView(), iblockdata1 != null ? org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(iblockdata1) : null);
                if (!event.callEvent()) {
                    return;
                } else if (event.getDamageState() == com.destroystokyo.paper.event.block.AnvilDamagedEvent.DamageState.BROKEN) {
                    iblockdata1 = null;
                } else {
                    iblockdata1 = ((org.bukkit.craftbukkit.block.data.CraftBlockData) event.getDamageState().getMaterial().createBlockData()).getState().setValue(AnvilBlock.FACING, iblockdata.getValue(AnvilBlock.FACING));
                }
                // Paper end - AnvilDamageEvent
                if (iblockdata1 == null) {
                    world.removeBlock(blockposition, false);
                    world.levelEvent(1029, blockposition, 0);
                } else {
                    world.setBlock(blockposition, iblockdata1, 2);
                    world.levelEvent(1030, blockposition, 0);
                }
            } else {
                world.levelEvent(1030, blockposition, 0);
            }

        });
    }

    @Override
    public void createResult() {
        ItemStack itemstack = this.inputSlots.getItem(0);

        this.cost.set(1);
        int i = 0;
        long j = 0L;
        byte b0 = 0;

        if (!itemstack.isEmpty() && EnchantmentHelper.canStoreEnchantments(itemstack)) {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            ItemEnchantments.Mutable itemenchantments_a = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemstack1));

            j += (long) (Integer) itemstack.getOrDefault(DataComponents.REPAIR_COST, 0) + (long) (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
            this.repairItemCountCost = 0;
            int k;

            if (!itemstack2.isEmpty()) {
                boolean flag = itemstack2.has(DataComponents.STORED_ENCHANTMENTS);
                int l;
                int i1;

                if (itemstack1.isDamageableItem() && itemstack1.getItem().isValidRepairItem(itemstack, itemstack2)) {
                    k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    if (k <= 0) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    for (i1 = 0; k > 0 && i1 < itemstack2.getCount(); ++i1) {
                        l = itemstack1.getDamageValue() - k;
                        itemstack1.setDamageValue(l);
                        ++i;
                        k = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i1;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        k = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        i1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        l = i1 + itemstack1.getMaxDamage() * 12 / 100;
                        int j1 = k + l;
                        int k1 = itemstack1.getMaxDamage() - j1;

                        if (k1 < 0) {
                            k1 = 0;
                        }

                        if (k1 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(k1);
                            i += 2;
                        }
                    }

                    ItemEnchantments itemenchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemstack2);
                    boolean flag1 = false;
                    boolean flag2 = false;
                    Iterator iterator = itemenchantments.entrySet().iterator();

                    while (iterator.hasNext()) {
                        Entry<Holder<Enchantment>> entry = (Entry) iterator.next();
                        Holder<Enchantment> holder = (Holder) entry.getKey();
                        int l1 = itemenchantments_a.getLevel(holder);
                        int i2 = entry.getIntValue();

                        i2 = l1 == i2 ? i2 + 1 : Math.max(i2, l1);
                        Enchantment enchantment = (Enchantment) holder.value();
                        boolean flag3 = enchantment.canEnchant(itemstack);

                        if (this.player.getAbilities().instabuild || itemstack.is(Items.ENCHANTED_BOOK)) {
                            flag3 = true;
                        }

                        Iterator iterator1 = itemenchantments_a.keySet().iterator();

                        while (iterator1.hasNext()) {
                            Holder<Enchantment> holder1 = (Holder) iterator1.next();

                            if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1)) {
                                flag3 = false;
                                ++i;
                            }
                        }

                        if (!flag3) {
                            flag2 = true;
                        } else {
                            flag1 = true;
                            if (i2 > enchantment.getMaxLevel()) {
                                i2 = enchantment.getMaxLevel();
                            }

                            itemenchantments_a.set(holder, i2);
                            int j2 = enchantment.getAnvilCost();

                            if (flag) {
                                j2 = Math.max(1, j2 / 2);
                            }

                            i += j2 * i2;
                            if (itemstack.getCount() > 1) {
                                i = 40;
                            }
                        }
                    }

                    if (flag2 && !flag1) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }
                }
            }

            if (this.itemName != null && !StringUtil.isBlank(this.itemName)) {
                if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                    b0 = 1;
                    i += b0;
                    itemstack1.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                }
            } else if (itemstack.has(DataComponents.CUSTOM_NAME)) {
                b0 = 1;
                i += b0;
                itemstack1.remove(DataComponents.CUSTOM_NAME);
            }

            int k2 = (int) Mth.clamp(j + (long) i, 0L, 2147483647L);

            this.cost.set(k2);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (b0 == i && b0 > 0 && this.cost.get() >= this.maximumRepairCost) { // CraftBukkit
                this.cost.set(this.maximumRepairCost - 1); // CraftBukkit
            }

            if (this.cost.get() >= this.maximumRepairCost && !this.player.getAbilities().instabuild) { // CraftBukkit
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                k = (Integer) itemstack1.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (k < (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    k = (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
                }

                if (b0 != i || b0 == 0) {
                    k = AnvilMenu.calculateIncreasedRepairCost(k);
                }

                itemstack1.set(DataComponents.REPAIR_COST, k);
                EnchantmentHelper.setEnchantments(itemstack1, itemenchantments_a.toImmutable());
            }

            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), itemstack1); // CraftBukkit
            this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
            this.broadcastChanges();
        } else {
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(this.getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(AnvilMenu.DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        }
    }

    public static int calculateIncreasedRepairCost(int cost) {
        return (int) Math.min((long) cost * 2L + 1L, 2147483647L);
    }

    public boolean setItemName(String newItemName) {
        String s1 = AnvilMenu.validateName(newItemName);

        if (s1 != null && !s1.equals(this.itemName)) {
            this.itemName = s1;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemstack = this.getSlot(2).getItem();

                if (StringUtil.isBlank(s1)) {
                    itemstack.remove(DataComponents.CUSTOM_NAME);
                } else {
                    itemstack.set(DataComponents.CUSTOM_NAME, Component.literal(s1));
                }
            }

            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static String validateName(String name) {
        String s1 = StringUtil.filterText(name);

        return s1.length() <= 50 ? s1 : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    // CraftBukkit start
    @Override
    public CraftAnvilView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryAnvil inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryAnvil(
                this.access.getLocation(), this.inputSlots, this.resultSlots);
        this.bukkitEntity = new CraftAnvilView(this.player.getBukkitEntity(), inventory, this);
        this.bukkitEntity.updateFromLegacy(inventory);
        return this.bukkitEntity;
    }
    // CraftBukkit end
}
