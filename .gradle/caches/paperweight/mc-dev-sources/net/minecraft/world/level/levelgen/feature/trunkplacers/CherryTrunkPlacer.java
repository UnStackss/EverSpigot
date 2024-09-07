package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class CherryTrunkPlacer extends TrunkPlacer {
    private static final Codec<UniformInt> BRANCH_START_CODEC = UniformInt.CODEC
        .codec()
        .validate(
            branchStartOffsetFromTop -> branchStartOffsetFromTop.getMaxValue() - branchStartOffsetFromTop.getMinValue() < 1
                    ? DataResult.error(() -> "Need at least 2 blocks variation for the branch starts to fit both branches")
                    : DataResult.success(branchStartOffsetFromTop)
        );
    public static final MapCodec<CherryTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance)
                .and(
                    instance.group(
                        IntProvider.codec(1, 3).fieldOf("branch_count").forGetter(trunkPlacer -> trunkPlacer.branchCount),
                        IntProvider.codec(2, 16).fieldOf("branch_horizontal_length").forGetter(trunkPlacer -> trunkPlacer.branchHorizontalLength),
                        IntProvider.validateCodec(-16, 0, BRANCH_START_CODEC)
                            .fieldOf("branch_start_offset_from_top")
                            .forGetter(trunkPlacer -> trunkPlacer.branchStartOffsetFromTop),
                        IntProvider.codec(-16, 16).fieldOf("branch_end_offset_from_top").forGetter(trunkPlacer -> trunkPlacer.branchEndOffsetFromTop)
                    )
                )
                .apply(instance, CherryTrunkPlacer::new)
    );
    private final IntProvider branchCount;
    private final IntProvider branchHorizontalLength;
    private final UniformInt branchStartOffsetFromTop;
    private final UniformInt secondBranchStartOffsetFromTop;
    private final IntProvider branchEndOffsetFromTop;

    public CherryTrunkPlacer(
        int baseHeight,
        int firstRandomHeight,
        int secondRandomHeight,
        IntProvider branchCount,
        IntProvider branchHorizontalLength,
        UniformInt branchStartOffsetFromTop,
        IntProvider branchEndOffsetFromTop
    ) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
        this.branchCount = branchCount;
        this.branchHorizontalLength = branchHorizontalLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
        this.secondBranchStartOffsetFromTop = UniformInt.of(branchStartOffsetFromTop.getMinValue(), branchStartOffsetFromTop.getMaxValue() - 1);
        this.branchEndOffsetFromTop = branchEndOffsetFromTop;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.CHERRY_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, int height, BlockPos startPos, TreeConfiguration config
    ) {
        setDirtAt(world, replacer, random, startPos.below(), config);
        int i = Math.max(0, height - 1 + this.branchStartOffsetFromTop.sample(random));
        int j = Math.max(0, height - 1 + this.secondBranchStartOffsetFromTop.sample(random));
        if (j >= i) {
            j++;
        }

        int k = this.branchCount.sample(random);
        boolean bl = k == 3;
        boolean bl2 = k >= 2;
        int l;
        if (bl) {
            l = height;
        } else if (bl2) {
            l = Math.max(i, j) + 1;
        } else {
            l = i + 1;
        }

        for (int o = 0; o < l; o++) {
            this.placeLog(world, replacer, random, startPos.above(o), config);
        }

        List<FoliagePlacer.FoliageAttachment> list = new ArrayList<>();
        if (bl) {
            list.add(new FoliagePlacer.FoliageAttachment(startPos.above(l), 0, false));
        }

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Function<BlockState, BlockState> function = state -> state.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
        list.add(this.generateBranch(world, replacer, random, height, startPos, config, function, direction, i, i < l - 1, mutableBlockPos));
        if (bl2) {
            list.add(this.generateBranch(world, replacer, random, height, startPos, config, function, direction.getOpposite(), j, j < l - 1, mutableBlockPos));
        }

        return list;
    }

    private FoliagePlacer.FoliageAttachment generateBranch(
        LevelSimulatedReader world,
        BiConsumer<BlockPos, BlockState> replacer,
        RandomSource random,
        int height,
        BlockPos startPos,
        TreeConfiguration config,
        Function<BlockState, BlockState> withAxisFunction,
        Direction direction,
        int branchStartOffset,
        boolean branchBelowHeight,
        BlockPos.MutableBlockPos mutablePos
    ) {
        mutablePos.set(startPos).move(Direction.UP, branchStartOffset);
        int i = height - 1 + this.branchEndOffsetFromTop.sample(random);
        boolean bl = branchBelowHeight || i < branchStartOffset;
        int j = this.branchHorizontalLength.sample(random) + (bl ? 1 : 0);
        BlockPos blockPos = startPos.relative(direction, j).above(i);
        int k = bl ? 2 : 1;

        for (int l = 0; l < k; l++) {
            this.placeLog(world, replacer, random, mutablePos.move(direction), config, withAxisFunction);
        }

        Direction direction2 = blockPos.getY() > mutablePos.getY() ? Direction.UP : Direction.DOWN;

        while (true) {
            int m = mutablePos.distManhattan(blockPos);
            if (m == 0) {
                return new FoliagePlacer.FoliageAttachment(blockPos.above(), 0, false);
            }

            float f = (float)Math.abs(blockPos.getY() - mutablePos.getY()) / (float)m;
            boolean bl2 = random.nextFloat() < f;
            mutablePos.move(bl2 ? direction2 : direction);
            this.placeLog(world, replacer, random, mutablePos, config, bl2 ? Function.identity() : withAxisFunction);
        }
    }
}
