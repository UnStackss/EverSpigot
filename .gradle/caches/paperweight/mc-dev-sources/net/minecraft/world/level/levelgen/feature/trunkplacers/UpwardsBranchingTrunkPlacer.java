package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class UpwardsBranchingTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<UpwardsBranchingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance)
                .and(
                    instance.group(
                        IntProvider.POSITIVE_CODEC.fieldOf("extra_branch_steps").forGetter(trunkPlacer -> trunkPlacer.extraBranchSteps),
                        Codec.floatRange(0.0F, 1.0F)
                            .fieldOf("place_branch_per_log_probability")
                            .forGetter(trunkPlacer -> trunkPlacer.placeBranchPerLogProbability),
                        IntProvider.NON_NEGATIVE_CODEC.fieldOf("extra_branch_length").forGetter(trunkPlacer -> trunkPlacer.extraBranchLength),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("can_grow_through").forGetter(trunkPlacer -> trunkPlacer.canGrowThrough)
                    )
                )
                .apply(instance, UpwardsBranchingTrunkPlacer::new)
    );
    private final IntProvider extraBranchSteps;
    private final float placeBranchPerLogProbability;
    private final IntProvider extraBranchLength;
    private final HolderSet<Block> canGrowThrough;

    public UpwardsBranchingTrunkPlacer(
        int baseHeight,
        int firstRandomHeight,
        int secondRandomHeight,
        IntProvider extraBranchSteps,
        float placeBranchPerLogProbability,
        IntProvider extraBranchLength,
        HolderSet<Block> canGrowThrough
    ) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
        this.extraBranchSteps = extraBranchSteps;
        this.placeBranchPerLogProbability = placeBranchPerLogProbability;
        this.extraBranchLength = extraBranchLength;
        this.canGrowThrough = canGrowThrough;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.UPWARDS_BRANCHING_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, int height, BlockPos startPos, TreeConfiguration config
    ) {
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < height; i++) {
            int j = startPos.getY() + i;
            if (this.placeLog(world, replacer, random, mutableBlockPos.set(startPos.getX(), j, startPos.getZ()), config)
                && i < height - 1
                && random.nextFloat() < this.placeBranchPerLogProbability) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int k = this.extraBranchLength.sample(random);
                int l = Math.max(0, k - this.extraBranchLength.sample(random) - 1);
                int m = this.extraBranchSteps.sample(random);
                this.placeBranch(world, replacer, random, height, config, list, mutableBlockPos, j, direction, l, m);
            }

            if (i == height - 1) {
                list.add(new FoliagePlacer.FoliageAttachment(mutableBlockPos.set(startPos.getX(), j + 1, startPos.getZ()), 0, false));
            }
        }

        return list;
    }

    private void placeBranch(
        LevelSimulatedReader world,
        BiConsumer<BlockPos, BlockState> replacer,
        RandomSource random,
        int height,
        TreeConfiguration config,
        List<FoliagePlacer.FoliageAttachment> nodes,
        BlockPos.MutableBlockPos pos,
        int yOffset,
        Direction direction,
        int length,
        int steps
    ) {
        int i = yOffset + length;
        int j = pos.getX();
        int k = pos.getZ();
        int l = length;

        while (l < height && steps > 0) {
            if (l >= 1) {
                int m = yOffset + l;
                j += direction.getStepX();
                k += direction.getStepZ();
                i = m;
                if (this.placeLog(world, replacer, random, pos.set(j, m, k), config)) {
                    i = m + 1;
                }

                nodes.add(new FoliagePlacer.FoliageAttachment(pos.immutable(), 0, false));
            }

            l++;
            steps--;
        }

        if (i - yOffset > 1) {
            BlockPos blockPos = new BlockPos(j, i, k);
            nodes.add(new FoliagePlacer.FoliageAttachment(blockPos, 0, false));
            nodes.add(new FoliagePlacer.FoliageAttachment(blockPos.below(2), 0, false));
        }
    }

    @Override
    protected boolean validTreePos(LevelSimulatedReader world, BlockPos pos) {
        return super.validTreePos(world, pos) || world.isStateAtPosition(pos, state -> state.is(this.canGrowThrough));
    }
}
