package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class MangroveRootPlacer extends RootPlacer {
    public static final int ROOT_WIDTH_LIMIT = 8;
    public static final int ROOT_LENGTH_LIMIT = 15;
    public static final MapCodec<MangroveRootPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> rootPlacerParts(instance)
                .and(MangroveRootPlacement.CODEC.fieldOf("mangrove_root_placement").forGetter(rootPlacer -> rootPlacer.mangroveRootPlacement))
                .apply(instance, MangroveRootPlacer::new)
    );
    private final MangroveRootPlacement mangroveRootPlacement;

    public MangroveRootPlacer(
        IntProvider trunkOffsetY, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement, MangroveRootPlacement mangroveRootPlacement
    ) {
        super(trunkOffsetY, rootProvider, aboveRootPlacement);
        this.mangroveRootPlacement = mangroveRootPlacement;
    }

    @Override
    public boolean placeRoots(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos pos, BlockPos trunkPos, TreeConfiguration config
    ) {
        List<BlockPos> list = Lists.newArrayList();
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        while (mutableBlockPos.getY() < trunkPos.getY()) {
            if (!this.canPlaceRoot(world, mutableBlockPos)) {
                return false;
            }

            mutableBlockPos.move(Direction.UP);
        }

        list.add(trunkPos.below());

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = trunkPos.relative(direction);
            List<BlockPos> list2 = Lists.newArrayList();
            if (!this.simulateRoots(world, random, blockPos, direction, trunkPos, list2, 0)) {
                return false;
            }

            list.addAll(list2);
            list.add(trunkPos.relative(direction));
        }

        for (BlockPos blockPos2 : list) {
            this.placeRoot(world, replacer, random, blockPos2, config);
        }

        return true;
    }

    private boolean simulateRoots(
        LevelSimulatedReader world, RandomSource random, BlockPos pos, Direction direction, BlockPos origin, List<BlockPos> offshootPositions, int rootLength
    ) {
        int i = this.mangroveRootPlacement.maxRootLength();
        if (rootLength != i && offshootPositions.size() <= i) {
            for (BlockPos blockPos : this.potentialRootPositions(pos, direction, random, origin)) {
                if (this.canPlaceRoot(world, blockPos)) {
                    offshootPositions.add(blockPos);
                    if (!this.simulateRoots(world, random, blockPos, direction, origin, offshootPositions, rootLength + 1)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    protected List<BlockPos> potentialRootPositions(BlockPos pos, Direction direction, RandomSource random, BlockPos origin) {
        BlockPos blockPos = pos.below();
        BlockPos blockPos2 = pos.relative(direction);
        int i = pos.distManhattan(origin);
        int j = this.mangroveRootPlacement.maxRootWidth();
        float f = this.mangroveRootPlacement.randomSkewChance();
        if (i > j - 3 && i <= j) {
            return random.nextFloat() < f ? List.of(blockPos, blockPos2.below()) : List.of(blockPos);
        } else if (i > j) {
            return List.of(blockPos);
        } else if (random.nextFloat() < f) {
            return List.of(blockPos);
        } else {
            return random.nextBoolean() ? List.of(blockPos2) : List.of(blockPos);
        }
    }

    @Override
    protected boolean canPlaceRoot(LevelSimulatedReader world, BlockPos pos) {
        return super.canPlaceRoot(world, pos) || world.isStateAtPosition(pos, state -> state.is(this.mangroveRootPlacement.canGrowThrough()));
    }

    @Override
    protected void placeRoot(LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos pos, TreeConfiguration config) {
        if (world.isStateAtPosition(pos, state -> state.is(this.mangroveRootPlacement.muddyRootsIn()))) {
            BlockState blockState = this.mangroveRootPlacement.muddyRootsProvider().getState(random, pos);
            replacer.accept(pos, this.getPotentiallyWaterloggedState(world, pos, blockState));
        } else {
            super.placeRoot(world, replacer, random, pos, config);
        }
    }

    @Override
    protected RootPlacerType<?> type() {
        return RootPlacerType.MANGROVE_ROOT_PLACER;
    }
}
