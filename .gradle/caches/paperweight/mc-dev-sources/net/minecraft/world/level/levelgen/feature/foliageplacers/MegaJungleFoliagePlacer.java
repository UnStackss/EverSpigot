package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class MegaJungleFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<MegaJungleFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
                .and(Codec.intRange(0, 16).fieldOf("height").forGetter(placer -> placer.height))
                .apply(instance, MegaJungleFoliagePlacer::new)
    );
    protected final int height;

    public MegaJungleFoliagePlacer(IntProvider radius, IntProvider offset, int height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.MEGA_JUNGLE_FOLIAGE_PLACER;
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
        int i = treeNode.doubleTrunk() ? foliageHeight : 1 + random.nextInt(2);

        for (int j = offset; j >= offset - i; j--) {
            int k = radius + treeNode.radiusOffset() + 1 - j;
            this.placeLeavesRow(world, placer, random, config, treeNode.pos(), k, j, treeNode.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        return dx + dz >= 7 || dx * dx + dz * dz > radius * radius;
    }
}
