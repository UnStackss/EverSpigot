package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class DarkOakFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<DarkOakFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance).apply(instance, DarkOakFoliagePlacer::new)
    );

    public DarkOakFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.DARK_OAK_FOLIAGE_PLACER;
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
        BlockPos blockPos = treeNode.pos().above(offset);
        boolean bl = treeNode.doubleTrunk();
        if (bl) {
            this.placeLeavesRow(world, placer, random, config, blockPos, radius + 2, -1, bl);
            this.placeLeavesRow(world, placer, random, config, blockPos, radius + 3, 0, bl);
            this.placeLeavesRow(world, placer, random, config, blockPos, radius + 2, 1, bl);
            if (random.nextBoolean()) {
                this.placeLeavesRow(world, placer, random, config, blockPos, radius, 2, bl);
            }
        } else {
            this.placeLeavesRow(world, placer, random, config, blockPos, radius + 2, -1, bl);
            this.placeLeavesRow(world, placer, random, config, blockPos, radius + 1, 0, bl);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return 4;
    }

    @Override
    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return y == 0 && giantTrunk && (dx == -radius || dx >= radius) && (dz == -radius || dz >= radius)
            || super.shouldSkipLocationSigned(random, dx, y, dz, radius, giantTrunk);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return y == -1 && !giantTrunk ? dx == radius && dz == radius : y == 1 && dx + dz > radius * 2 - 2;
    }
}
