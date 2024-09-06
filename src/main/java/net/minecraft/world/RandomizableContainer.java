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

    default void setLootTable(ResourceKey<LootTable> lootTableId, long lootTableSeed) {
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
            if (nbt.contains("LootTableSeed", 4)) {
                this.setLootTableSeed(nbt.getLong("LootTableSeed"));
            } else {
                this.setLootTableSeed(0L);
            }

            return true;
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
            long l = this.getLootTableSeed();
            if (l != 0L) {
                nbt.putLong("LootTableSeed", l);
            }

            return true;
        }
    }

    default void unpackLootTable(@Nullable Player player) {
        Level level = this.getLevel();
        BlockPos blockPos = this.getBlockPos();
        ResourceKey<LootTable> resourceKey = this.getLootTable();
        if (resourceKey != null && level != null && level.getServer() != null) {
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(resourceKey);
            if (player instanceof ServerPlayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer)player, resourceKey);
            }

            this.setLootTable(null);
            LootParams.Builder builder = new LootParams.Builder((ServerLevel)level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos));
            if (player != null) {
                builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
            }

            lootTable.fill(this, builder.create(LootContextParamSets.CHEST), this.getLootTableSeed());
        }
    }
}
