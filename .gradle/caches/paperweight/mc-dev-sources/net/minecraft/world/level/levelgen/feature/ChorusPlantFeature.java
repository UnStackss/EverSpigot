package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class ChorusPlantFeature extends Feature<NoneFeatureConfiguration> {
    public ChorusPlantFeature(Codec<NoneFeatureConfiguration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        RandomSource randomSource = context.random();
        if (worldGenLevel.isEmptyBlock(blockPos) && worldGenLevel.getBlockState(blockPos.below()).is(Blocks.END_STONE)) {
            ChorusFlowerBlock.generatePlant(worldGenLevel, blockPos, randomSource, 8);
            return true;
        } else {
            return false;
        }
    }
}
