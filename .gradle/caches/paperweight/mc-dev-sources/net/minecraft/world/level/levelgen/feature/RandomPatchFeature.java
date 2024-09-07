package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;

public class RandomPatchFeature extends Feature<RandomPatchConfiguration> {
    public RandomPatchFeature(Codec<RandomPatchConfiguration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RandomPatchConfiguration> context) {
        RandomPatchConfiguration randomPatchConfiguration = context.config();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        int i = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int j = randomPatchConfiguration.xzSpread() + 1;
        int k = randomPatchConfiguration.ySpread() + 1;

        for (int l = 0; l < randomPatchConfiguration.tries(); l++) {
            mutableBlockPos.setWithOffset(
                blockPos,
                randomSource.nextInt(j) - randomSource.nextInt(j),
                randomSource.nextInt(k) - randomSource.nextInt(k),
                randomSource.nextInt(j) - randomSource.nextInt(j)
            );
            if (randomPatchConfiguration.feature().value().place(worldGenLevel, context.chunkGenerator(), randomSource, mutableBlockPos)) {
                i++;
            }
        }

        return i > 0;
    }
}
