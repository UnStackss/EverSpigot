package net.minecraft.commands.arguments.blocks;

import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockInput implements Predicate<BlockInWorld> {
    private final BlockState state;
    private final Set<Property<?>> properties;
    @Nullable
    public final CompoundTag tag;

    public BlockInput(BlockState state, Set<Property<?>> properties, @Nullable CompoundTag data) {
        this.state = state;
        this.properties = properties;
        this.tag = data;
    }

    public BlockState getState() {
        return this.state;
    }

    public Set<Property<?>> getDefinedProperties() {
        return this.properties;
    }

    @Override
    public boolean test(BlockInWorld blockInWorld) {
        BlockState blockState = blockInWorld.getState();
        if (!blockState.is(this.state.getBlock())) {
            return false;
        } else {
            for (Property<?> property : this.properties) {
                if (blockState.getValue(property) != this.state.getValue(property)) {
                    return false;
                }
            }

            if (this.tag == null) {
                return true;
            } else {
                BlockEntity blockEntity = blockInWorld.getEntity();
                return blockEntity != null && NbtUtils.compareNbt(this.tag, blockEntity.saveWithFullMetadata(blockInWorld.getLevel().registryAccess()), true);
            }
        }
    }

    public boolean test(ServerLevel world, BlockPos pos) {
        return this.test(new BlockInWorld(world, pos, false));
    }

    public boolean place(ServerLevel world, BlockPos pos, int flags) {
        BlockState blockState = Block.updateFromNeighbourShapes(this.state, world, pos);
        if (blockState.isAir()) {
            blockState = this.state;
        }

        if (!world.setBlock(pos, blockState, flags)) {
            return false;
        } else {
            if (this.tag != null) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null) {
                    blockEntity.loadWithComponents(this.tag, world.registryAccess());
                }
            }

            return true;
        }
    }
}
