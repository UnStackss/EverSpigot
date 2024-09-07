package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class CherryFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<CherryFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
                .and(
                    instance.group(
                        IntProvider.codec(4, 16).fieldOf("height").forGetter(foliagePlacer -> foliagePlacer.height),
                        Codec.floatRange(0.0F, 1.0F)
                            .fieldOf("wide_bottom_layer_hole_chance")
                            .forGetter(foliagePlacer -> foliagePlacer.wideBottomLayerHoleChance),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("corner_hole_chance").forGetter(foliagePlacer -> foliagePlacer.wideBottomLayerHoleChance),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_chance").forGetter(foliagePlacer -> foliagePlacer.hangingLeavesChance),
                        Codec.floatRange(0.0F, 1.0F)
                            .fieldOf("hanging_leaves_extension_chance")
                            .forGetter(foliagePlacer -> foliagePlacer.hangingLeavesExtensionChance)
                    )
                )
                .apply(instance, CherryFoliagePlacer::new)
    );
    private final IntProvider height;
    private final float wideBottomLayerHoleChance;
    private final float cornerHoleChance;
    private final float hangingLeavesChance;
    private final float hangingLeavesExtensionChance;

    public CherryFoliagePlacer(
        IntProvider radius,
        IntProvider offset,
        IntProvider height,
        float wideBottomLayerHoleChance,
        float cornerHoleChance,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        super(radius, offset);
        this.height = height;
        this.wideBottomLayerHoleChance = wideBottomLayerHoleChance;
        this.cornerHoleChance = cornerHoleChance;
        this.hangingLeavesChance = hangingLeavesChance;
        this.hangingLeavesExtensionChance = hangingLeavesExtensionChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.CHERRY_FOLIAGE_PLACER;
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
        int i = radius + treeNode.radiusOffset() - 1;
        this.placeLeavesRow(world, placer, random, config, blockPos, i - 2, foliageHeight - 3, bl);
        this.placeLeavesRow(world, placer, random, config, blockPos, i - 1, foliageHeight - 4, bl);

        for (int j = foliageHeight - 5; j >= 0; j--) {
            this.placeLeavesRow(world, placer, random, config, blockPos, i, j, bl);
        }

        this.placeLeavesRowWithHangingLeavesBelow(
            world, placer, random, config, blockPos, i, -1, bl, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
        this.placeLeavesRowWithHangingLeavesBelow(
            world, placer, random, config, blockPos, i - 1, -2, bl, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
    }

    @Override
    public int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        if (y == -1 && (dx == radius || dz == radius) && random.nextFloat() < this.wideBottomLayerHoleChance) {
            return true;
        } else {
            boolean bl = dx == radius && dz == radius;
            boolean bl2 = radius > 2;
            return bl2 ? bl || dx + dz > radius * 2 - 2 && random.nextFloat() < this.cornerHoleChance : bl && random.nextFloat() < this.cornerHoleChance;
        }
    }
}
