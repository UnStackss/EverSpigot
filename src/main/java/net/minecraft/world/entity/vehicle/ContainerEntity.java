package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface ContainerEntity extends Container, MenuProvider {
    Vec3 position();

    AABB getBoundingBox();

    @Nullable
    ResourceKey<LootTable> getLootTable();

    void setLootTable(@Nullable ResourceKey<LootTable> lootTable);

    long getLootTableSeed();

    void setLootTableSeed(long lootTableSeed);

    NonNullList<ItemStack> getItemStacks();

    void clearItemStacks();

    Level level();

    boolean isRemoved();

    @Override
    default boolean isEmpty() {
        return this.isChestVehicleEmpty();
    }

    default void addChestVehicleSaveData(CompoundTag nbt, HolderLookup.Provider registriesLookup) {
        if (this.getLootTable() != null) {
            nbt.putString("LootTable", this.getLootTable().location().toString());
            this.lootableData().saveNbt(nbt); // Paper
            if (this.getLootTableSeed() != 0L) {
                nbt.putLong("LootTableSeed", this.getLootTableSeed());
            }
        }
        ContainerHelper.saveAllItems(nbt, this.getItemStacks(), registriesLookup); // Paper - always save the items, table may still remain
    }

    default void readChestVehicleSaveData(CompoundTag nbt, HolderLookup.Provider registriesLookup) {
        this.clearItemStacks();
        if (nbt.contains("LootTable", 8)) {
            this.setLootTable(net.minecraft.Optionull.map(ResourceLocation.tryParse(nbt.getString("LootTable")), rl -> ResourceKey.create(Registries.LOOT_TABLE, rl))); // Paper - Validate ResourceLocation
            // Paper start - LootTable API
            if (this.getLootTable() != null) {
                this.lootableData().loadNbt(nbt);
            }
            // Paper end - LootTable API
            this.setLootTableSeed(nbt.getLong("LootTableSeed"));
        }
        ContainerHelper.loadAllItems(nbt, this.getItemStacks(), registriesLookup); // Paper - always save the items, table may still remain
    }

    default void chestVehicleDestroyed(DamageSource source, Level world, Entity vehicle) {
        if (world.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            Containers.dropContents(world, vehicle, this);
            if (!world.isClientSide) {
                Entity entity = source.getDirectEntity();
                if (entity != null && entity.getType() == EntityType.PLAYER) {
                    PiglinAi.angerNearbyPiglins((Player)entity, true);
                }
            }
        }
    }

    default InteractionResult interactWithContainerVehicle(Player player) {
        // Paper start - Fix InventoryOpenEvent cancellation
        if (player.openMenu(this).isEmpty()) {
            return InteractionResult.PASS;
        }
        // Paper end - Fix InventoryOpenEvent cancellation
        return !player.level().isClientSide ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }

    default void unpackChestVehicleLootTable(@Nullable Player player) {
        MinecraftServer minecraftServer = this.level().getServer();
        if (minecraftServer != null && this.lootableData().shouldReplenish(this, com.destroystokyo.paper.loottable.PaperLootableInventoryData.ENTITY, player)) { // Paper - LootTable API
            LootTable lootTable = minecraftServer.reloadableRegistries().getLootTable(this.getLootTable());
            if (player != null) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer)player, this.getLootTable());
            }

            // Paper start - LootTable API
            if (this.lootableData().shouldClearLootTable(this, com.destroystokyo.paper.loottable.PaperLootableInventoryData.ENTITY, player)) {
                this.setLootTable(null);
            }
            // Paper end - LootTable API
            LootParams.Builder builder = new LootParams.Builder((ServerLevel)this.level()).withParameter(LootContextParams.ORIGIN, this.position());
            if (player != null) {
                builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
            }

            lootTable.fill(this, builder.create(LootContextParamSets.CHEST), this.getLootTableSeed());
        }
    }

    default void clearChestVehicleContent() {
        this.unpackChestVehicleLootTable(null);
        this.getItemStacks().clear();
    }

    default boolean isChestVehicleEmpty() {
        for (ItemStack itemStack : this.getItemStacks()) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    default ItemStack removeChestVehicleItemNoUpdate(int slot) {
        this.unpackChestVehicleLootTable(null);
        ItemStack itemStack = this.getItemStacks().get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            this.getItemStacks().set(slot, ItemStack.EMPTY);
            return itemStack;
        }
    }

    default ItemStack getChestVehicleItem(int slot) {
        this.unpackChestVehicleLootTable(null);
        return this.getItemStacks().get(slot);
    }

    default ItemStack removeChestVehicleItem(int slot, int amount) {
        this.unpackChestVehicleLootTable(null);
        return ContainerHelper.removeItem(this.getItemStacks(), slot, amount);
    }

    default void setChestVehicleItem(int slot, ItemStack stack) {
        this.unpackChestVehicleLootTable(null);
        this.getItemStacks().set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
    }

    default SlotAccess getChestVehicleSlot(int slot) {
        return slot >= 0 && slot < this.getContainerSize() ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return ContainerEntity.this.getChestVehicleItem(slot);
            }

            @Override
            public boolean set(ItemStack stack) {
                ContainerEntity.this.setChestVehicleItem(slot, stack);
                return true;
            }
        } : SlotAccess.NULL;
    }

    default boolean isChestVehicleStillValid(Player player) {
        return !this.isRemoved() && player.canInteractWithEntity(this.getBoundingBox(), 4.0);
    }

    // Paper start - LootTable API
    default com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData() {
        throw new UnsupportedOperationException("Implement this method");
    }

    default com.destroystokyo.paper.loottable.PaperLootableInventory getLootableInventory() {
        return ((com.destroystokyo.paper.loottable.PaperLootableInventory) ((net.minecraft.world.entity.Entity) this).getBukkitEntity());
    }
    // Paper end - LootTable API
}
