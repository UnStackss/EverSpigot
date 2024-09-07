package net.minecraft.world.level.block;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public interface SculkBehaviour {
    SculkBehaviour DEFAULT = new SculkBehaviour() {
        @Override
        public boolean attemptSpreadVein(
            LevelAccessor world, BlockPos pos, BlockState state, @Nullable Collection<Direction> directions, boolean markForPostProcessing
        ) {
            if (directions == null) {
                return ((SculkVeinBlock)Blocks.SCULK_VEIN).getSameSpaceSpreader().spreadAll(world.getBlockState(pos), world, pos, markForPostProcessing) > 0L;
            } else {
                return !directions.isEmpty()
                    ? (state.isAir() || state.getFluidState().is(Fluids.WATER)) && SculkVeinBlock.regrow(world, pos, state, directions)
                    : SculkBehaviour.super.attemptSpreadVein(world, pos, state, directions, markForPostProcessing);
            }
        }

        @Override
        public int attemptUseCharge(
            SculkSpreader.ChargeCursor cursor,
            LevelAccessor world,
            BlockPos catalystPos,
            RandomSource random,
            SculkSpreader spreadManager,
            boolean shouldConvertToBlock
        ) {
            return cursor.getDecayDelay() > 0 ? cursor.getCharge() : 0;
        }

        @Override
        public int updateDecayDelay(int oldDecay) {
            return Math.max(oldDecay - 1, 0);
        }
    };

    default byte getSculkSpreadDelay() {
        return 1;
    }

    default void onDischarged(LevelAccessor world, BlockState state, BlockPos pos, RandomSource random) {
    }

    default boolean depositCharge(LevelAccessor world, BlockPos pos, RandomSource random) {
        return false;
    }

    default boolean attemptSpreadVein(
        LevelAccessor world, BlockPos pos, BlockState state, @Nullable Collection<Direction> directions, boolean markForPostProcessing
    ) {
        return ((MultifaceBlock)Blocks.SCULK_VEIN).getSpreader().spreadAll(state, world, pos, markForPostProcessing) > 0L;
    }

    default boolean canChangeBlockStateOnSpread() {
        return true;
    }

    default int updateDecayDelay(int oldDecay) {
        return 1;
    }

    int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor,
        LevelAccessor world,
        BlockPos catalystPos,
        RandomSource random,
        SculkSpreader spreadManager,
        boolean shouldConvertToBlock
    );
}
