package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class FancyTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<FancyTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, FancyTrunkPlacer::new)
    );
    private static final double TRUNK_HEIGHT_SCALE = 0.618;
    private static final double CLUSTER_DENSITY_MAGIC = 1.382;
    private static final double BRANCH_SLOPE = 0.381;
    private static final double BRANCH_LENGTH_MAGIC = 0.328;

    public FancyTrunkPlacer(int baseHeight, int firstRandomHeight, int secondRandomHeight) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FANCY_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, int height, BlockPos startPos, TreeConfiguration config
    ) {
        int i = 5;
        int j = height + 2;
        int k = Mth.floor((double)j * 0.618);
        setDirtAt(world, replacer, random, startPos.below(), config);
        double d = 1.0;
        int l = Math.min(1, Mth.floor(1.382 + Math.pow(1.0 * (double)j / 13.0, 2.0)));
        int m = startPos.getY() + k;
        int n = j - 5;
        List<FancyTrunkPlacer.FoliageCoords> list = Lists.newArrayList();
        list.add(new FancyTrunkPlacer.FoliageCoords(startPos.above(n), m));

        for (; n >= 0; n--) {
            float f = treeShape(j, n);
            if (!(f < 0.0F)) {
                for (int o = 0; o < l; o++) {
                    double e = 1.0;
                    double g = 1.0 * (double)f * ((double)random.nextFloat() + 0.328);
                    double h = (double)(random.nextFloat() * 2.0F) * Math.PI;
                    double p = g * Math.sin(h) + 0.5;
                    double q = g * Math.cos(h) + 0.5;
                    BlockPos blockPos = startPos.offset(Mth.floor(p), n - 1, Mth.floor(q));
                    BlockPos blockPos2 = blockPos.above(5);
                    if (this.makeLimb(world, replacer, random, blockPos, blockPos2, false, config)) {
                        int r = startPos.getX() - blockPos.getX();
                        int s = startPos.getZ() - blockPos.getZ();
                        double t = (double)blockPos.getY() - Math.sqrt((double)(r * r + s * s)) * 0.381;
                        int u = t > (double)m ? m : (int)t;
                        BlockPos blockPos3 = new BlockPos(startPos.getX(), u, startPos.getZ());
                        if (this.makeLimb(world, replacer, random, blockPos3, blockPos, false, config)) {
                            list.add(new FancyTrunkPlacer.FoliageCoords(blockPos, blockPos3.getY()));
                        }
                    }
                }
            }
        }

        this.makeLimb(world, replacer, random, startPos, startPos.above(k), true, config);
        this.makeBranches(world, replacer, random, j, startPos, list, config);
        List<FoliagePlacer.FoliageAttachment> list2 = Lists.newArrayList();

        for (FancyTrunkPlacer.FoliageCoords foliageCoords : list) {
            if (this.trimBranches(j, foliageCoords.getBranchBase() - startPos.getY())) {
                list2.add(foliageCoords.attachment);
            }
        }

        return list2;
    }

    private boolean makeLimb(
        LevelSimulatedReader world,
        BiConsumer<BlockPos, BlockState> replacer,
        RandomSource random,
        BlockPos startPos,
        BlockPos branchPos,
        boolean make,
        TreeConfiguration config
    ) {
        if (!make && Objects.equals(startPos, branchPos)) {
            return true;
        } else {
            BlockPos blockPos = branchPos.offset(-startPos.getX(), -startPos.getY(), -startPos.getZ());
            int i = this.getSteps(blockPos);
            float f = (float)blockPos.getX() / (float)i;
            float g = (float)blockPos.getY() / (float)i;
            float h = (float)blockPos.getZ() / (float)i;

            for (int j = 0; j <= i; j++) {
                BlockPos blockPos2 = startPos.offset(Mth.floor(0.5F + (float)j * f), Mth.floor(0.5F + (float)j * g), Mth.floor(0.5F + (float)j * h));
                if (make) {
                    this.placeLog(
                        world, replacer, random, blockPos2, config, state -> state.trySetValue(RotatedPillarBlock.AXIS, this.getLogAxis(startPos, blockPos2))
                    );
                } else if (!this.isFree(world, blockPos2)) {
                    return false;
                }
            }

            return true;
        }
    }

    private int getSteps(BlockPos offset) {
        int i = Mth.abs(offset.getX());
        int j = Mth.abs(offset.getY());
        int k = Mth.abs(offset.getZ());
        return Math.max(i, Math.max(j, k));
    }

    private Direction.Axis getLogAxis(BlockPos branchStart, BlockPos branchEnd) {
        Direction.Axis axis = Direction.Axis.Y;
        int i = Math.abs(branchEnd.getX() - branchStart.getX());
        int j = Math.abs(branchEnd.getZ() - branchStart.getZ());
        int k = Math.max(i, j);
        if (k > 0) {
            if (i == k) {
                axis = Direction.Axis.X;
            } else {
                axis = Direction.Axis.Z;
            }
        }

        return axis;
    }

    private boolean trimBranches(int treeHeight, int height) {
        return (double)height >= (double)treeHeight * 0.2;
    }

    private void makeBranches(
        LevelSimulatedReader world,
        BiConsumer<BlockPos, BlockState> replacer,
        RandomSource random,
        int treeHeight,
        BlockPos startPos,
        List<FancyTrunkPlacer.FoliageCoords> branchPositions,
        TreeConfiguration config
    ) {
        for (FancyTrunkPlacer.FoliageCoords foliageCoords : branchPositions) {
            int i = foliageCoords.getBranchBase();
            BlockPos blockPos = new BlockPos(startPos.getX(), i, startPos.getZ());
            if (!blockPos.equals(foliageCoords.attachment.pos()) && this.trimBranches(treeHeight, i - startPos.getY())) {
                this.makeLimb(world, replacer, random, blockPos, foliageCoords.attachment.pos(), true, config);
            }
        }
    }

    private static float treeShape(int treeHeight, int height) {
        if ((float)height < (float)treeHeight * 0.3F) {
            return -1.0F;
        } else {
            float f = (float)treeHeight / 2.0F;
            float g = f - (float)height;
            float h = Mth.sqrt(f * f - g * g);
            if (g == 0.0F) {
                h = f;
            } else if (Math.abs(g) >= f) {
                return 0.0F;
            }

            return h * 0.5F;
        }
    }

    static class FoliageCoords {
        final FoliagePlacer.FoliageAttachment attachment;
        private final int branchBase;

        public FoliageCoords(BlockPos pos, int width) {
            this.attachment = new FoliagePlacer.FoliageAttachment(pos, 0, false);
            this.branchBase = width;
        }

        public int getBranchBase() {
            return this.branchBase;
        }
    }
}
