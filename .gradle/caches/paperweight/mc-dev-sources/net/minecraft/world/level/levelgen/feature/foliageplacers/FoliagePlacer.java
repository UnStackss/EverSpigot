package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products.P2;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.material.Fluids;

public abstract class FoliagePlacer {
    public static final Codec<FoliagePlacer> CODEC = BuiltInRegistries.FOLIAGE_PLACER_TYPE
        .byNameCodec()
        .dispatch(FoliagePlacer::type, FoliagePlacerType::codec);
    protected final IntProvider radius;
    protected final IntProvider offset;

    protected static <P extends FoliagePlacer> P2<Mu<P>, IntProvider, IntProvider> foliagePlacerParts(Instance<P> instance) {
        return instance.group(
            IntProvider.codec(0, 16).fieldOf("radius").forGetter(placer -> placer.radius),
            IntProvider.codec(0, 16).fieldOf("offset").forGetter(placer -> placer.offset)
        );
    }

    public FoliagePlacer(IntProvider radius, IntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    protected abstract FoliagePlacerType<?> type();

    public void createFoliage(
        LevelSimulatedReader world,
        FoliagePlacer.FoliageSetter placer,
        RandomSource random,
        TreeConfiguration config,
        int trunkHeight,
        FoliagePlacer.FoliageAttachment treeNode,
        int foliageHeight,
        int radius
    ) {
        this.createFoliage(world, placer, random, config, trunkHeight, treeNode, foliageHeight, radius, this.offset(random));
    }

    protected abstract void createFoliage(
        LevelSimulatedReader world,
        FoliagePlacer.FoliageSetter placer,
        RandomSource random,
        TreeConfiguration config,
        int trunkHeight,
        FoliagePlacer.FoliageAttachment treeNode,
        int foliageHeight,
        int radius,
        int offset
    );

    public abstract int foliageHeight(RandomSource random, int trunkHeight, TreeConfiguration config);

    public int foliageRadius(RandomSource random, int baseHeight) {
        return this.radius.sample(random);
    }

    private int offset(RandomSource random) {
        return this.offset.sample(random);
    }

    protected abstract boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk);

    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        int i;
        int j;
        if (giantTrunk) {
            i = Math.min(Math.abs(dx), Math.abs(dx - 1));
            j = Math.min(Math.abs(dz), Math.abs(dz - 1));
        } else {
            i = Math.abs(dx);
            j = Math.abs(dz);
        }

        return this.shouldSkipLocation(random, i, y, j, radius, giantTrunk);
    }

    protected void placeLeavesRow(
        LevelSimulatedReader world,
        FoliagePlacer.FoliageSetter placer,
        RandomSource random,
        TreeConfiguration config,
        BlockPos centerPos,
        int radius,
        int y,
        boolean giantTrunk
    ) {
        int i = giantTrunk ? 1 : 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int j = -radius; j <= radius + i; j++) {
            for (int k = -radius; k <= radius + i; k++) {
                if (!this.shouldSkipLocationSigned(random, j, y, k, radius, giantTrunk)) {
                    mutableBlockPos.setWithOffset(centerPos, j, y, k);
                    tryPlaceLeaf(world, placer, random, config, mutableBlockPos);
                }
            }
        }
    }

    protected final void placeLeavesRowWithHangingLeavesBelow(
        LevelSimulatedReader world,
        FoliagePlacer.FoliageSetter placer,
        RandomSource random,
        TreeConfiguration config,
        BlockPos centerPos,
        int radius,
        int y,
        boolean giantTrunk,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        this.placeLeavesRow(world, placer, random, config, centerPos, radius, y, giantTrunk);
        int i = giantTrunk ? 1 : 0;
        BlockPos blockPos = centerPos.below();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction direction2 = direction.getClockWise();
            int j = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? radius + i : radius;
            mutableBlockPos.setWithOffset(centerPos, 0, y - 1, 0).move(direction2, j).move(direction, -radius);
            int k = -radius;

            while (k < radius + i) {
                boolean bl = placer.isSet(mutableBlockPos.move(Direction.UP));
                mutableBlockPos.move(Direction.DOWN);
                if (bl && tryPlaceExtension(world, placer, random, config, hangingLeavesChance, blockPos, mutableBlockPos)) {
                    mutableBlockPos.move(Direction.DOWN);
                    tryPlaceExtension(world, placer, random, config, hangingLeavesExtensionChance, blockPos, mutableBlockPos);
                    mutableBlockPos.move(Direction.UP);
                }

                k++;
                mutableBlockPos.move(direction);
            }
        }
    }

    private static boolean tryPlaceExtension(
        LevelSimulatedReader world,
        FoliagePlacer.FoliageSetter placer,
        RandomSource random,
        TreeConfiguration config,
        float chance,
        BlockPos origin,
        BlockPos.MutableBlockPos pos
    ) {
        return pos.distManhattan(origin) < 7 && !(random.nextFloat() > chance) && tryPlaceLeaf(world, placer, random, config, pos);
    }

    protected static boolean tryPlaceLeaf(
        LevelSimulatedReader world, FoliagePlacer.FoliageSetter placer, RandomSource random, TreeConfiguration config, BlockPos pos
    ) {
        if (!TreeFeature.validTreePos(world, pos)) {
            return false;
        } else {
            BlockState blockState = config.foliageProvider.getState(random, pos);
            if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                blockState = blockState.setValue(
                    BlockStateProperties.WATERLOGGED, Boolean.valueOf(world.isFluidAtPosition(pos, fluidState -> fluidState.isSourceOfType(Fluids.WATER)))
                );
            }

            placer.set(pos, blockState);
            return true;
        }
    }

    public static final class FoliageAttachment {
        private final BlockPos pos;
        private final int radiusOffset;
        private final boolean doubleTrunk;

        public FoliageAttachment(BlockPos center, int foliageRadius, boolean giantTrunk) {
            this.pos = center;
            this.radiusOffset = foliageRadius;
            this.doubleTrunk = giantTrunk;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public int radiusOffset() {
            return this.radiusOffset;
        }

        public boolean doubleTrunk() {
            return this.doubleTrunk;
        }
    }

    public interface FoliageSetter {
        void set(BlockPos pos, BlockState state);

        boolean isSet(BlockPos pos);
    }
}
