package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkBehaviour;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.SculkPatchConfiguration;

public class SculkPatchFeature extends Feature<SculkPatchConfiguration> {
    public SculkPatchFeature(Codec<SculkPatchConfiguration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SculkPatchConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        if (!this.canSpreadFrom(worldGenLevel, blockPos)) {
            return false;
        } else {
            SculkPatchConfiguration sculkPatchConfiguration = context.config();
            RandomSource randomSource = context.random();
            SculkSpreader sculkSpreader = SculkSpreader.createWorldGenSpreader();
            int i = sculkPatchConfiguration.spreadRounds() + sculkPatchConfiguration.growthRounds();

            for (int j = 0; j < i; j++) {
                for (int k = 0; k < sculkPatchConfiguration.chargeCount(); k++) {
                    sculkSpreader.addCursors(blockPos, sculkPatchConfiguration.amountPerCharge());
                }

                boolean bl = j < sculkPatchConfiguration.spreadRounds();

                for (int l = 0; l < sculkPatchConfiguration.spreadAttempts(); l++) {
                    sculkSpreader.updateCursors(worldGenLevel, blockPos, randomSource, bl);
                }

                sculkSpreader.clear();
            }

            BlockPos blockPos2 = blockPos.below();
            if (randomSource.nextFloat() <= sculkPatchConfiguration.catalystChance()
                && worldGenLevel.getBlockState(blockPos2).isCollisionShapeFullBlock(worldGenLevel, blockPos2)) {
                worldGenLevel.setBlock(blockPos, Blocks.SCULK_CATALYST.defaultBlockState(), 3);
            }

            int m = sculkPatchConfiguration.extraRareGrowths().sample(randomSource);

            for (int n = 0; n < m; n++) {
                BlockPos blockPos3 = blockPos.offset(randomSource.nextInt(5) - 2, 0, randomSource.nextInt(5) - 2);
                if (worldGenLevel.getBlockState(blockPos3).isAir()
                    && worldGenLevel.getBlockState(blockPos3.below()).isFaceSturdy(worldGenLevel, blockPos3.below(), Direction.UP)) {
                    worldGenLevel.setBlock(
                        blockPos3, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, Boolean.valueOf(true)), 3
                    );
                }
            }

            return true;
        }
    }

    private boolean canSpreadFrom(LevelAccessor world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.getBlock() instanceof SculkBehaviour
            || (blockState.isAir() || blockState.is(Blocks.WATER) && blockState.getFluidState().isSource())
                && Direction.stream().map(pos::relative).anyMatch(pos2 -> world.getBlockState(pos2).isCollisionShapeFullBlock(world, pos2));
    }
}
