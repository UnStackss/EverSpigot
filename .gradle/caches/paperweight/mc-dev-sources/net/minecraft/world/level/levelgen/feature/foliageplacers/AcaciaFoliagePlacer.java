package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class AcaciaFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<AcaciaFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance).apply(instance, AcaciaFoliagePlacer::new)
    );

    public AcaciaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.ACACIA_FOLIAGE_PLACER;
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
        boolean bl = treeNode.doubleTrunk();
        BlockPos blockPos = treeNode.pos().above(offset);
        this.placeLeavesRow(world, placer, random, config, blockPos, radius + treeNode.radiusOffset(), -1 - foliageHeight, bl);
        this.placeLeavesRow(world, placer, random, config, blockPos, radius - 1, -foliageHeight, bl);
        this.placeLeavesRow(world, placer, random, config, blockPos, radius + treeNode.radiusOffset() - 1, 0, bl);
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return y == 0 ? (dx > 1 || dz > 1) && dx != 0 && dz != 0 : dx == radius && dz == radius && radius > 0;
    }
}
