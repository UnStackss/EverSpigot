package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class BlastFurnaceBlock extends AbstractFurnaceBlock {
    public static final MapCodec<BlastFurnaceBlock> CODEC = simpleCodec(BlastFurnaceBlock::new);

    @Override
    public MapCodec<BlastFurnaceBlock> codec() {
        return CODEC;
    }

    protected BlastFurnaceBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createFurnaceTicker(world, type, BlockEntityType.BLAST_FURNACE);
    }

    @Override
    protected void openContainer(Level world, BlockPos pos, Player player) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BlastFurnaceBlockEntity) {
            player.openMenu((MenuProvider)blockEntity);
            player.awardStat(Stats.INTERACT_WITH_BLAST_FURNACE);
        }
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            double d = (double)pos.getX() + 0.5;
            double e = (double)pos.getY();
            double f = (double)pos.getZ() + 0.5;
            if (random.nextDouble() < 0.1) {
                world.playLocalSound(d, e, f, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }

            Direction direction = state.getValue(FACING);
            Direction.Axis axis = direction.getAxis();
            double g = 0.52;
            double h = random.nextDouble() * 0.6 - 0.3;
            double i = axis == Direction.Axis.X ? (double)direction.getStepX() * 0.52 : h;
            double j = random.nextDouble() * 9.0 / 16.0;
            double k = axis == Direction.Axis.Z ? (double)direction.getStepZ() * 0.52 : h;
            world.addParticle(ParticleTypes.SMOKE, d + i, e + j, f + k, 0.0, 0.0, 0.0);
        }
    }
}