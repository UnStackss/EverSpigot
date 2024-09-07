package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class BlobFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<BlobFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(instance -> blobParts(instance).apply(instance, BlobFoliagePlacer::new));
    protected final int height;

    protected static <P extends BlobFoliagePlacer> P3<Mu<P>, IntProvider, IntProvider, Integer> blobParts(Instance<P> builder) {
        return foliagePlacerParts(builder).and(Codec.intRange(0, 16).fieldOf("height").forGetter(placer -> placer.height));
    }

    public BlobFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.BLOB_FOLIAGE_PLACER;
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
        for (int i = offset; i >= offset - foliageHeight; i--) {
            int j = Math.max(radius + treeNode.radiusOffset() - 1 - i / 2, 0);
            this.placeLeavesRow(world, placer, random, config, treeNode.pos(), j, i, treeNode.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return dx == radius && dz == radius && (random.nextInt(2) == 0 || y == 0);
    }
}
