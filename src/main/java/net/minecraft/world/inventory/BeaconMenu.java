package net.minecraft.world.inventory;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.craftbukkit.inventory.view.CraftBeaconView;
// CraftBukkit end

public class BeaconMenu extends AbstractContainerMenu {

    private static final int PAYMENT_SLOT = 0;
    private static final int SLOT_COUNT = 1;
    private static final int DATA_COUNT = 3;
    private static final int INV_SLOT_START = 1;
    private static final int INV_SLOT_END = 28;
    private static final int USE_ROW_SLOT_START = 28;
    private static final int USE_ROW_SLOT_END = 37;
    private static final int NO_EFFECT = 0;
    private final Container beacon;
    private final BeaconMenu.PaymentSlot paymentSlot;
    private final ContainerLevelAccess access;
    private final ContainerData beaconData;
    // CraftBukkit start
    private CraftBeaconView bukkitEntity = null;
    private Inventory player;
    // CraftBukkit end

    public BeaconMenu(int syncId, Container inventory) {
        this(syncId, inventory, new SimpleContainerData(3), ContainerLevelAccess.NULL);
    }

    public BeaconMenu(int syncId, Container inventory, ContainerData propertyDelegate, ContainerLevelAccess context) {
        super(MenuType.BEACON, syncId);
        this.player = (Inventory) inventory; // CraftBukkit - TODO: check this
        this.beacon = new SimpleContainer(1) { // CraftBukkit - decompile error
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return stack.is(ItemTags.BEACON_PAYMENT_ITEMS);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        checkContainerDataCount(propertyDelegate, 3);
        this.beaconData = propertyDelegate;
        this.access = context;
        this.paymentSlot = new BeaconMenu.PaymentSlot(this, this.beacon, 0, 136, 110);
        this.addSlot(this.paymentSlot);
        this.addDataSlots(propertyDelegate);
        boolean flag = true;
        boolean flag1 = true;

        int j;

        for (j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(inventory, k + j * 9 + 9, 36 + k * 18, 137 + j * 18));
            }
        }

        for (j = 0; j < 9; ++j) {
            this.addSlot(new Slot(inventory, j, 36 + j * 18, 195));
        }

    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack itemstack = this.paymentSlot.remove(this.paymentSlot.getMaxStackSize());

            if (!itemstack.isEmpty()) {
                player.drop(itemstack, false);
            }

        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.BEACON);
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot1 = (Slot) this.slots.get(slot);

        if (slot1 != null && slot1.hasItem()) {
            ItemStack itemstack1 = slot1.getItem();

            itemstack = itemstack1.copy();
            if (slot == 0) {
                if (!this.moveItemStackTo(itemstack1, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }

                slot1.onQuickCraft(itemstack1, itemstack);
            } else if (!this.paymentSlot.hasItem() && this.paymentSlot.mayPlace(itemstack1) && itemstack1.getCount() == 1) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot >= 1 && slot < 28) {
                if (!this.moveItemStackTo(itemstack1, 28, 37, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot >= 28 && slot < 37) {
                if (!this.moveItemStackTo(itemstack1, 1, 28, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 1, 37, false)) {
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

    public int getLevels() {
        return this.beaconData.get(0);
    }

    public static int encodeEffect(@Nullable Holder<MobEffect> effect) {
        return effect == null ? 0 : BuiltInRegistries.MOB_EFFECT.asHolderIdMap().getId(effect) + 1;
    }

    @Nullable
    public static Holder<MobEffect> decodeEffect(int id) {
        return id == 0 ? null : (Holder) BuiltInRegistries.MOB_EFFECT.asHolderIdMap().byId(id - 1);
    }

    @Nullable
    public Holder<MobEffect> getPrimaryEffect() {
        return BeaconMenu.decodeEffect(this.beaconData.get(1));
    }

    @Nullable
    public Holder<MobEffect> getSecondaryEffect() {
        return BeaconMenu.decodeEffect(this.beaconData.get(2));
    }

    // Paper start - Add PlayerChangeBeaconEffectEvent
    private static @Nullable org.bukkit.potion.PotionEffectType convert(Optional<Holder<MobEffect>> optionalEffect) {
        return optionalEffect.map(org.bukkit.craftbukkit.potion.CraftPotionEffectType::minecraftHolderToBukkit).orElse(null);
    }
    // Paper end - Add PlayerChangeBeaconEffectEvent

    public void updateEffects(Optional<Holder<MobEffect>> primary, Optional<Holder<MobEffect>> secondary) {
        // Paper start - fix MC-174630 - validate secondary power
        if (secondary.isPresent() && secondary.get() != net.minecraft.world.effect.MobEffects.REGENERATION && (primary.isPresent() && secondary.get() != primary.get())) {
            secondary = Optional.empty();
        }
        // Paper end
        if (this.paymentSlot.hasItem()) {
            // Paper start - Add PlayerChangeBeaconEffectEvent
            io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent event = new io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent((org.bukkit.entity.Player) this.player.player.getBukkitEntity(), convert(primary), convert(secondary), this.access.getLocation().getBlock());
            if (event.callEvent()) {
                // Paper end - Add PlayerChangeBeaconEffectEvent
                this.beaconData.set(1, BeaconMenu.encodeEffect(event.getPrimary() == null ? null : org.bukkit.craftbukkit.potion.CraftPotionEffectType.bukkitToMinecraftHolder(event.getPrimary())));// CraftBukkit - decompile error
                this.beaconData.set(2, BeaconMenu.encodeEffect(event.getSecondary() == null ? null : org.bukkit.craftbukkit.potion.CraftPotionEffectType.bukkitToMinecraftHolder(event.getSecondary())));// CraftBukkit - decompile error
            if (event.willConsumeItem()) { // Paper
            this.paymentSlot.remove(1);
            } // Paper
            this.access.execute(Level::blockEntityChanged);
            } // Paper end - Add PlayerChangeBeaconEffectEvent
        }

    }

    public boolean hasPayment() {
        return !this.beacon.getItem(0).isEmpty();
    }

    private class PaymentSlot extends Slot {

        public PaymentSlot(final BeaconMenu inventory, final Container index, final int x, final int y, final int k) {
            super(index, x, y, k);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.BEACON_PAYMENT_ITEMS);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // CraftBukkit start
    @Override
    public CraftBeaconView getBukkitView() {
        if (this.bukkitEntity != null) {
            return this.bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventory inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryBeacon(this.beacon);
        this.bukkitEntity = new CraftBeaconView(this.player.player.getBukkitEntity(), inventory, this);
        return this.bukkitEntity;
    }
    // CraftBukkit end
}
