package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractBannerBlock extends BaseEntityBlock {
    private final DyeColor color;

    protected AbstractBannerBlock(DyeColor color, BlockBehaviour.Properties settings) {
        super(settings);
        this.color = color;
    }

    @Override
    protected abstract MapCodec<? extends AbstractBannerBlock> codec();

    @Override
    public boolean isPossibleToRespawnInThis(BlockState state) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BannerBlockEntity(pos, state, this.color);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return world.getBlockEntity(pos) instanceof BannerBlockEntity bannerBlockEntity
            ? bannerBlockEntity.getItem()
            : super.getCloneItemStack(world, pos, state);
    }

    public DyeColor getColor() {
        return this.color;
    }
}
