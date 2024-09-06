package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class DropExperienceBlock extends Block {

    public static final MapCodec<DropExperienceBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(IntProvider.codec(0, 10).fieldOf("experience").forGetter((dropexperienceblock) -> {
            return dropexperienceblock.xpRange;
        }), propertiesCodec()).apply(instance, DropExperienceBlock::new);
    });
    private final IntProvider xpRange;

    @Override
    public MapCodec<? extends DropExperienceBlock> codec() {
        return DropExperienceBlock.CODEC;
    }

    public DropExperienceBlock(IntProvider experienceDropped, BlockBehaviour.Properties settings) {
        super(settings);
        this.xpRange = experienceDropped;
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, world, pos, tool, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState iblockdata, ServerLevel worldserver, BlockPos blockposition, ItemStack itemstack, boolean flag) {
        if (flag) {
            return this.tryDropExperience(worldserver, blockposition, itemstack, this.xpRange);
        }

        return 0;
        // CraftBukkit end
    }
}
