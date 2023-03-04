package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class IceBlock extends HalfTransparentBlock {

    public static final MapCodec<IceBlock> CODEC = simpleCodec(IceBlock::new);

    @Override
    public MapCodec<? extends IceBlock> codec() {
        return IceBlock.CODEC;
    }

    public IceBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    public static BlockState meltsInto() {
        return Blocks.WATER.defaultBlockState();
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool, boolean includeDrops, boolean dropExp) { // Paper - fix drops not preventing stats/food exhaustion
        super.playerDestroy(world, player, pos, state, blockEntity, tool, includeDrops, dropExp); // Paper - fix drops not preventing stats/food exhaustion
        // Paper start - Improve Block#breakNaturally API
        this.afterDestroy(world, pos, tool);
    }
    public void afterDestroy(Level world, BlockPos pos, ItemStack tool) {
        // Paper end - Improve Block#breakNaturally API
        if (!EnchantmentHelper.hasTag(tool, EnchantmentTags.PREVENTS_ICE_MELTING)) {
            if (world.dimensionType().ultraWarm()) {
                world.removeBlock(pos, false);
                return;
            }

            BlockState iblockdata1 = world.getBlockState(pos.below());

            if (iblockdata1.blocksMotion() || iblockdata1.liquid()) {
                world.setBlockAndUpdate(pos, IceBlock.meltsInto());
            }
        }

    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.getBrightness(LightLayer.BLOCK, pos) > 11 - state.getLightBlock(world, pos)) {
            this.melt(state, world, pos);
        }

    }

    protected void melt(BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, world.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState()).isCancelled()) {
            return;
        }
        // CraftBukkit end
        if (world.dimensionType().ultraWarm()) {
            world.removeBlock(pos, false);
        } else {
            world.setBlockAndUpdate(pos, IceBlock.meltsInto());
            world.neighborChanged(pos, IceBlock.meltsInto().getBlock(), pos);
        }
    }
}
