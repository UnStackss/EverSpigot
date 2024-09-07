package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class MegaPineFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<MegaPineFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
                .and(IntProvider.codec(0, 24).fieldOf("crown_height").forGetter(placer -> placer.crownHeight))
                .apply(instance, MegaPineFoliagePlacer::new)
    );
    private final IntProvider crownHeight;

    public MegaPineFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider crownHeight) {
        super(radius, offset);
        this.crownHeight = crownHeight;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.MEGA_PINE_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(
        LevelSimulatedReader world,
        FoliagePlacer.FoliageSetter placer,
        RandomSource random,
        TreeConfiguration config,
        int trunkHeight,
        FoliagePlacer.FoliageAttachment treeNode,
        int foliageHeight,
        int radius,
        int offset
    ) {
        BlockPos blockPos = treeNode.pos();
        int i = 0;

        for (int j = blockPos.getY() - foliageHeight + offset; j <= blockPos.getY() + offset; j++) {
            int k = blockPos.getY() - j;
            int l = radius + treeNode.radiusOffset() + Mth.floor((float)k / (float)foliageHeight * 3.5F);
            int m;
            if (k > 0 && l == i && (j & 1) == 0) {
                m = l + 1;
            } else {
                m = l;
            }

            this.placeLeavesRow(world, placer, random, config, new BlockPos(blockPos.getX(), j, blockPos.getZ()), m, 0, treeNode.doubleTrunk());
            i = l;
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return this.crownHeight.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return dx + dz >= 7 || dx * dx + dz * dz > radius * radius;
    }
}
