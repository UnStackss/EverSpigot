package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;

public class DiskFeature extends Feature<DiskConfiguration> {
    public DiskFeature(Codec<DiskConfiguration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DiskConfiguration> context) {
        DiskConfiguration diskConfiguration = context.config();
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        boolean bl = false;
        int i = blockPos.getY();
        int j = i + diskConfiguration.halfHeight();
        int k = i - diskConfiguration.halfHeight() - 1;
        int l = diskConfiguration.radius().sample(randomSource);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-l, 0, -l), blockPos.offset(l, 0, l))) {
            int m = blockPos2.getX() - blockPos.getX();
            int n = blockPos2.getZ() - blockPos.getZ();
            if (m * m + n * n <= l * l) {
                bl |= this.placeColumn(diskConfiguration, worldGenLevel, randomSource, j, k, mutableBlockPos.set(blockPos2));
            }
        }

        return bl;
    }

    protected boolean placeColumn(DiskConfiguration config, WorldGenLevel world, RandomSource random, int topY, int bottomY, BlockPos.MutableBlockPos pos) {
        boolean bl = false;

        for (int i = topY; i > bottomY; i--) {
            pos.setY(i);
            if (config.target().test(world, pos)) {
                BlockState blockState = config.stateProvider().getState(world, random, pos);
                world.setBlock(pos, blockState, 2);
                this.markAboveForPostProcessing(world, pos);
                bl = true;
            }
        }

        return bl;
    }
}
