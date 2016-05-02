package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public abstract class AbstractMinecartContainer extends AbstractMinecart implements ContainerEntity {

    private NonNullList<ItemStack> itemStacks;
    @Nullable
    public ResourceKey<LootTable> lootTable;
    public long lootTableSeed;

    // Paper start - LootTable API
    final com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData = new com.destroystokyo.paper.loottable.PaperLootableInventoryData();

    @Override
    public com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData() {
        return this.lootableData;
    }
    // Paper end - LootTable API
    // CraftBukkit start
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;

    public List<ItemStack> getContents() {
        return this.itemStacks;
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

    public InventoryHolder getOwner() {
        org.bukkit.entity.Entity cart = this.getBukkitEntity();
        if(cart instanceof InventoryHolder) return (InventoryHolder) cart;
        return null;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public Location getLocation() {
        return this.getBukkitEntity().getLocation();
    }
    // CraftBukkit end

    protected AbstractMinecartContainer(EntityType<?> type, Level world) {
        super(type, world);
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); // CraftBukkit - SPIGOT-3513
    }

    protected AbstractMinecartContainer(EntityType<?> type, double x, double y, double z, Level world) {
        super(type, world, x, y, z);
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY); // CraftBukkit - SPIGOT-3513
    }

    @Override
    public void destroy(DamageSource source) {
        super.destroy(source);
        this.chestVehicleDestroyed(source, this.level(), this);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getChestVehicleItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return this.removeChestVehicleItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.removeChestVehicleItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.setChestVehicleItem(slot, stack);
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return this.getChestVehicleSlot(mappedIndex);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return this.isChestVehicleStillValid(player);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(reason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        // CraftBukkit end
        if (!this.level().isClientSide && entity_removalreason.shouldDestroy()) {
            Containers.dropContents(this.level(), (Entity) this, (Container) this);
        }

        super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        this.addChestVehicleSaveData(nbt, this.registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.readChestVehicleSaveData(nbt, this.registryAccess());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return this.interactWithContainerVehicle(player);
    }

    @Override
    protected void applyNaturalSlowdown() {
        float f = 0.98F;

        if (this.lootTable == null) {
            int i = 15 - AbstractContainerMenu.getRedstoneSignalFromContainer(this);

            f += (float) i * 0.001F;
        }

        if (this.isInWater()) {
            f *= 0.95F;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.0D, (double) f));
    }

    @Override
    public void clearContent() {
        this.clearChestVehicleContent();
    }

    public void setLootTable(ResourceKey<LootTable> lootTable, long lootSeed) {
        this.lootTable = lootTable;
        this.lootTableSeed = lootSeed;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        if (this.lootTable != null && player.isSpectator()) {
            return null;
        } else {
            this.unpackChestVehicleLootTable(playerInventory.player);
            return this.createMenu(syncId, playerInventory);
        }
    }

    protected abstract AbstractContainerMenu createMenu(int syncId, Inventory playerInventory);

    @Nullable
    @Override
    public ResourceKey<LootTable> getLootTable() {
        return this.lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> lootTable) {
        this.lootTable = lootTable;
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long lootTableSeed) {
        this.lootTableSeed = lootTableSeed;
    }

    @Override
    public NonNullList<ItemStack> getItemStacks() {
        return this.itemStacks;
    }

    @Override
    public void clearItemStacks() {
        this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
    }
}
