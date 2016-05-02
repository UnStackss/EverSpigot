package net.minecraft.world;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public interface RandomizableContainer extends Container {
    String LOOT_TABLE_TAG = "LootTable";
    String LOOT_TABLE_SEED_TAG = "LootTableSeed";

    @Nullable
    ResourceKey<LootTable> getLootTable();

    void setLootTable(@Nullable ResourceKey<LootTable> lootTable);

    default void setLootTable(@Nullable ResourceKey<LootTable> lootTableId, long lootTableSeed) { // Paper - add nullable
        this.setLootTable(lootTableId);
        this.setLootTableSeed(lootTableSeed);
    }

    long getLootTableSeed();

    void setLootTableSeed(long lootTableSeed);

    BlockPos getBlockPos();

    @Nullable
    Level getLevel();

    static void setBlockEntityLootTable(BlockGetter world, RandomSource random, BlockPos pos, ResourceKey<LootTable> lootTableId) {
        if (world.getBlockEntity(pos) instanceof RandomizableContainer randomizableContainer) {
            randomizableContainer.setLootTable(lootTableId, random.nextLong());
        }
    }

    default boolean tryLoadLootTable(CompoundTag nbt) {
        if (nbt.contains("LootTable", 8)) {
            this.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(nbt.getString("LootTable"))));
            if (this.lootableData() != null && this.getLootTable() != null) this.lootableData().loadNbt(nbt); // Paper - LootTable API
            if (nbt.contains("LootTableSeed", 4)) {
                this.setLootTableSeed(nbt.getLong("LootTableSeed"));
            } else {
                this.setLootTableSeed(0L);
            }

            return this.lootableData() == null; // Paper - only track the loot table if there is chance for replenish
        } else {
            return false;
        }
    }

    default boolean trySaveLootTable(CompoundTag nbt) {
        ResourceKey<LootTable> resourceKey = this.getLootTable();
        if (resourceKey == null) {
            return false;
        } else {
            nbt.putString("LootTable", resourceKey.location().toString());
            if (this.lootableData() != null) this.lootableData().saveNbt(nbt); // Paper - LootTable API
            long l = this.getLootTableSeed();
            if (l != 0L) {
                nbt.putLong("LootTableSeed", l);
            }

            return this.lootableData() == null; // Paper - only track the loot table if there is chance for replenish
        }
    }

    default void unpackLootTable(@Nullable Player player) {
        Level level = this.getLevel();
        BlockPos blockPos = this.getBlockPos();
        ResourceKey<LootTable> resourceKey = this.getLootTable();
        if (resourceKey != null && level != null && level.getServer() != null && (this.lootableData() == null || this.lootableData().shouldReplenish(this, com.destroystokyo.paper.loottable.PaperLootableInventoryData.CONTAINER, player))) { // Paper - LootTable API
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(resourceKey);
            if (player instanceof ServerPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer)player, resourceKey);
            }

            // Paper start - LootTable API
            if (this.lootableData() == null || this.lootableData().shouldClearLootTable(this, com.destroystokyo.paper.loottable.PaperLootableInventoryData.CONTAINER, player)) {
                this.setLootTable(null);
            }
            // Paper end - LootTable API
            LootParams.Builder builder = new LootParams.Builder((ServerLevel)level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos));
            if (player != null) {
                builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
            }

            lootTable.fill(this, builder.create(LootContextParamSets.CHEST), this.getLootTableSeed());
        }
    }

    // Paper start - LootTable API
    @Nullable @org.jetbrains.annotations.Contract(pure = true)
    default com.destroystokyo.paper.loottable.PaperLootableInventoryData lootableData() {
        return null; // some containers don't really have a "replenish" ability like decorated pots
    }

    default com.destroystokyo.paper.loottable.PaperLootableInventory getLootableInventory() {
        final org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(java.util.Objects.requireNonNull(this.getLevel(), "Cannot manage loot tables on block entities not in world"), this.getBlockPos());
        return (com.destroystokyo.paper.loottable.PaperLootableInventory) block.getState(false);
    }
    // Paper end - LootTable API
}
