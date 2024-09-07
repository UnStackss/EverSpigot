package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class SpruceFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<SpruceFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
                .and(IntProvider.codec(0, 24).fieldOf("trunk_height").forGetter(placer -> placer.trunkHeight))
                .apply(instance, SpruceFoliagePlacer::new)
    );
    private final IntProvider trunkHeight;

    public SpruceFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider trunkHeight) {
        super(radius, offset);
        this.trunkHeight = trunkHeight;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.SPRUCE_FOLIAGE_PLACER;
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
        int i = random.nextInt(2);
        int j = 1;
        int k = 0;

        for (int l = offset; l >= -foliageHeight; l--) {
            this.placeLeavesRow(world, placer, random, config, blockPos, i, l, treeNode.doubleTrunk());
            if (i >= j) {
                i = k;
                k = 1;
                j = Math.min(j + 1, radius + treeNode.radiusOffset());
            } else {
                i++;
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return Math.max(4, trunkHeight - this.trunkHeight.sample(random));
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return dx == radius && dz == radius && radius > 0;
    }
}
