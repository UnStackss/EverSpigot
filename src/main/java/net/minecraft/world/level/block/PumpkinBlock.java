package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

public class PumpkinBlock extends Block {
    public static final MapCodec<PumpkinBlock> CODEC = simpleCodec(PumpkinBlock::new);

    @Override
    public MapCodec<PumpkinBlock> codec() {
        return CODEC;
    }

    protected PumpkinBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (!stack.is(Items.SHEARS)) {
            return super.useItemOn(stack, state, world, pos, player, hand, hit);
        } else if (world.isClientSide) {
            return ItemInteractionResult.sidedSuccess(world.isClientSide);
        } else {
            Direction direction = hit.getDirection();
            Direction direction2 = direction.getAxis() == Direction.Axis.Y ? player.getDirection().getOpposite() : direction;
            world.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
            world.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, direction2), 11);
            ItemEntity itemEntity = new ItemEntity(
                world,
                (double)pos.getX() + 0.5 + (double)direction2.getStepX() * 0.65,
                (double)pos.getY() + 0.1,
                (double)pos.getZ() + 0.5 + (double)direction2.getStepZ() * 0.65,
                new ItemStack(Items.PUMPKIN_SEEDS, 4)
            );
            itemEntity.setDeltaMovement(
                0.05 * (double)direction2.getStepX() + world.random.nextDouble() * 0.02,
                0.05,
                0.05 * (double)direction2.getStepZ() + world.random.nextDouble() * 0.02
            );
            world.addFreshEntity(itemEntity);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            world.gameEvent(player, GameEvent.SHEAR, pos);
            player.awardStat(Stats.ITEM_USED.get(Items.SHEARS));
            return ItemInteractionResult.sidedSuccess(world.isClientSide);
        }
    }
}
