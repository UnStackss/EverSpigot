package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerBlockEntity extends BlockEntity implements Spawner {
    private final BaseSpawner spawner = new BaseSpawner() {
        @Override
        public void broadcastEvent(Level world, BlockPos pos, int status) {
            world.blockEvent(pos, Blocks.SPAWNER, status, 0);
        }

        @Override
        public void setNextSpawnData(@Nullable Level world, BlockPos pos, SpawnData spawnEntry) {
            super.setNextSpawnData(world, pos, spawnEntry);
            if (world != null) {
                BlockState blockState = world.getBlockState(pos);
                world.sendBlockUpdated(pos, blockState, blockState, 4);
            }
        }
    };

    public SpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.MOB_SPAWNER, pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.spawner.load(this.level, this.worldPosition, nbt);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        this.spawner.save(nbt);
    }

    public static void clientTick(Level world, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity) {
        blockEntity.spawner.clientTick(world, pos);
    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity) {
        blockEntity.spawner.serverTick((ServerLevel)world, pos);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        CompoundTag compoundTag = this.saveCustomOnly(registryLookup);
        compoundTag.remove("SpawnPotentials");
        return compoundTag;
    }

    @Override
    public boolean triggerEvent(int type, int data) {
        return this.spawner.onEventTriggered(this.level, type) || super.triggerEvent(type, data);
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public void setEntityId(EntityType<?> type, RandomSource random) {
        this.spawner.setEntityId(type, this.level, random, this.worldPosition);
        this.setChanged();
    }

    public BaseSpawner getSpawner() {
        return this.spawner;
    }
}
