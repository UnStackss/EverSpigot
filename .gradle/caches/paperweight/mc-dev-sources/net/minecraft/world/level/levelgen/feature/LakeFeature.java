package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

@Deprecated
public class LakeFeature extends Feature<LakeFeature.Configuration> {
    private static final BlockState AIR = Blocks.CAVE_AIR.defaultBlockState();

    public LakeFeature(Codec<LakeFeature.Configuration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean place(FeaturePlaceContext<LakeFeature.Configuration> context) {
        BlockPos blockPos = context.origin();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        LakeFeature.Configuration configuration = context.config();
        if (blockPos.getY() <= worldGenLevel.getMinBuildHeight() + 4) {
            return false;
        } else {
            blockPos = blockPos.below(4);
            boolean[] bls = new boolean[2048];
            int i = randomSource.nextInt(4) + 4;

            for (int j = 0; j < i; j++) {
                double d = randomSource.nextDouble() * 6.0 + 3.0;
                double e = randomSource.nextDouble() * 4.0 + 2.0;
                double f = randomSource.nextDouble() * 6.0 + 3.0;
                double g = randomSource.nextDouble() * (16.0 - d - 2.0) + 1.0 + d / 2.0;
                double h = randomSource.nextDouble() * (8.0 - e - 4.0) + 2.0 + e / 2.0;
                double k = randomSource.nextDouble() * (16.0 - f - 2.0) + 1.0 + f / 2.0;

                for (int l = 1; l < 15; l++) {
                    for (int m = 1; m < 15; m++) {
                        for (int n = 1; n < 7; n++) {
                            double o = ((double)l - g) / (d / 2.0);
                            double p = ((double)n - h) / (e / 2.0);
                            double q = ((double)m - k) / (f / 2.0);
                            double r = o * o + p * p + q * q;
                            if (r < 1.0) {
                                bls[(l * 16 + m) * 8 + n] = true;
                            }
                        }
                    }
                }
            }

            BlockState blockState = configuration.fluid().getState(randomSource, blockPos);

            for (int s = 0; s < 16; s++) {
                for (int t = 0; t < 16; t++) {
                    for (int u = 0; u < 8; u++) {
                        boolean bl = !bls[(s * 16 + t) * 8 + u]
                            && (
                                s < 15 && bls[((s + 1) * 16 + t) * 8 + u]
                                    || s > 0 && bls[((s - 1) * 16 + t) * 8 + u]
                                    || t < 15 && bls[(s * 16 + t + 1) * 8 + u]
                                    || t > 0 && bls[(s * 16 + (t - 1)) * 8 + u]
                                    || u < 7 && bls[(s * 16 + t) * 8 + u + 1]
                                    || u > 0 && bls[(s * 16 + t) * 8 + (u - 1)]
                            );
                        if (bl) {
                            BlockState blockState2 = worldGenLevel.getBlockState(blockPos.offset(s, u, t));
                            if (u >= 4 && blockState2.liquid()) {
                                return false;
                            }

                            if (u < 4 && !blockState2.isSolid() && worldGenLevel.getBlockState(blockPos.offset(s, u, t)) != blockState) {
                                return false;
                            }
                        }
                    }
                }
            }

            for (int v = 0; v < 16; v++) {
                for (int w = 0; w < 16; w++) {
                    for (int x = 0; x < 8; x++) {
                        if (bls[(v * 16 + w) * 8 + x]) {
                            BlockPos blockPos2 = blockPos.offset(v, x, w);
                            if (this.canReplaceBlock(worldGenLevel.getBlockState(blockPos2))) {
                                boolean bl2 = x >= 4;
                                worldGenLevel.setBlock(blockPos2, bl2 ? AIR : blockState, 2);
                                if (bl2) {
                                    worldGenLevel.scheduleTick(blockPos2, AIR.getBlock(), 0);
                                    this.markAboveForPostProcessing(worldGenLevel, blockPos2);
                                }
                            }
                        }
                    }
                }
            }

            BlockState blockState3 = configuration.barrier().getState(randomSource, blockPos);
            if (!blockState3.isAir()) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int aa = 0; aa < 8; aa++) {
                            boolean bl3 = !bls[(y * 16 + z) * 8 + aa]
                                && (
                                    y < 15 && bls[((y + 1) * 16 + z) * 8 + aa]
                                        || y > 0 && bls[((y - 1) * 16 + z) * 8 + aa]
                                        || z < 15 && bls[(y * 16 + z + 1) * 8 + aa]
                                        || z > 0 && bls[(y * 16 + (z - 1)) * 8 + aa]
                                        || aa < 7 && bls[(y * 16 + z) * 8 + aa + 1]
                                        || aa > 0 && bls[(y * 16 + z) * 8 + (aa - 1)]
                                );
                            if (bl3 && (aa < 4 || randomSource.nextInt(2) != 0)) {
                                BlockState blockState4 = worldGenLevel.getBlockState(blockPos.offset(y, aa, z));
                                if (blockState4.isSolid() && !blockState4.is(BlockTags.LAVA_POOL_STONE_CANNOT_REPLACE)) {
                                    BlockPos blockPos3 = blockPos.offset(y, aa, z);
                                    worldGenLevel.setBlock(blockPos3, blockState3, 2);
                                    this.markAboveForPostProcessing(worldGenLevel, blockPos3);
                                }
                            }
                        }
                    }
                }
            }

            if (blockState.getFluidState().is(FluidTags.WATER)) {
                for (int ab = 0; ab < 16; ab++) {
                    for (int ac = 0; ac < 16; ac++) {
                        int ad = 4;
                        BlockPos blockPos4 = blockPos.offset(ab, 4, ac);
                        if (worldGenLevel.getBiome(blockPos4).value().shouldFreeze(worldGenLevel, blockPos4, false)
                            && this.canReplaceBlock(worldGenLevel.getBlockState(blockPos4))) {
                            worldGenLevel.setBlock(blockPos4, Blocks.ICE.defaultBlockState(), 2);
                        }
                    }
                }
            }

            return true;
        }
    }

    private boolean canReplaceBlock(BlockState state) {
        return !state.is(BlockTags.FEATURES_CANNOT_REPLACE);
    }

    public static record Configuration(BlockStateProvider fluid, BlockStateProvider barrier) implements FeatureConfiguration {
        public static final Codec<LakeFeature.Configuration> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        BlockStateProvider.CODEC.fieldOf("fluid").forGetter(LakeFeature.Configuration::fluid),
                        BlockStateProvider.CODEC.fieldOf("barrier").forGetter(LakeFeature.Configuration::barrier)
                    )
                    .apply(instance, LakeFeature.Configuration::new)
        );
    }
}
