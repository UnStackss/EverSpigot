package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class ScatteredOreFeature extends Feature<OreConfiguration> {
    private static final int MAX_DIST_FROM_ORIGIN = 7;

    ScatteredOreFeature(Codec<OreConfiguration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        OreConfiguration oreConfiguration = context.config();
        BlockPos blockPos = context.origin();
        int i = randomSource.nextInt(oreConfiguration.size + 1);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < i; j++) {
            this.offsetTargetPos(mutableBlockPos, randomSource, blockPos, Math.min(j, 7));
            BlockState blockState = worldGenLevel.getBlockState(mutableBlockPos);

            for (OreConfiguration.TargetBlockState targetBlockState : oreConfiguration.targetStates) {
                if (OreFeature.canPlaceOre(blockState, worldGenLevel::getBlockState, randomSource, oreConfiguration, targetBlockState, mutableBlockPos)) {
                    worldGenLevel.setBlock(mutableBlockPos, targetBlockState.state, 2);
                    break;
                }
            }
        }

        return true;
    }

    private void offsetTargetPos(BlockPos.MutableBlockPos mutable, RandomSource random, BlockPos origin, int spread) {
        int i = this.getRandomPlacementInOneAxisRelativeToOrigin(random, spread);
        int j = this.getRandomPlacementInOneAxisRelativeToOrigin(random, spread);
        int k = this.getRandomPlacementInOneAxisRelativeToOrigin(random, spread);
        mutable.setWithOffset(origin, i, j, k);
    }

    private int getRandomPlacementInOneAxisRelativeToOrigin(RandomSource random, int spread) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float)spread);
    }
}
