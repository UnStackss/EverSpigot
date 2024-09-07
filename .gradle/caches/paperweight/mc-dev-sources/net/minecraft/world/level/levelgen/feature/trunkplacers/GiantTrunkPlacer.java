package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class GiantTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<GiantTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, GiantTrunkPlacer::new)
    );

    public GiantTrunkPlacer(int baseHeight, int firstRandomHeight, int secondRandomHeight) {
        super(baseHeight, firstRandomHeight, secondRandomHeight);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.GIANT_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, int height, BlockPos startPos, TreeConfiguration config
    ) {
        BlockPos blockPos = startPos.below();
        setDirtAt(world, replacer, random, blockPos, config);
        setDirtAt(world, replacer, random, blockPos.east(), config);
        setDirtAt(world, replacer, random, blockPos.south(), config);
        setDirtAt(world, replacer, random, blockPos.south().east(), config);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < height; i++) {
            this.placeLogIfFreeWithOffset(world, replacer, random, mutableBlockPos, config, startPos, 0, i, 0);
            if (i < height - 1) {
                this.placeLogIfFreeWithOffset(world, replacer, random, mutableBlockPos, config, startPos, 1, i, 0);
                this.placeLogIfFreeWithOffset(world, replacer, random, mutableBlockPos, config, startPos, 1, i, 1);
                this.placeLogIfFreeWithOffset(world, replacer, random, mutableBlockPos, config, startPos, 0, i, 1);
            }
        }

        return ImmutableList.of(new FoliagePlacer.FoliageAttachment(startPos.above(height), 0, true));
    }

    private void placeLogIfFreeWithOffset(
        LevelSimulatedReader world,
        BiConsumer<BlockPos, BlockState> replacer,
        RandomSource random,
        BlockPos.MutableBlockPos tmpPos,
        TreeConfiguration config,
        BlockPos startPos,
        int dx,
        int dy,
        int dz
    ) {
        tmpPos.setWithOffset(startPos, dx, dy, dz);
        this.placeLogIfFree(world, replacer, random, tmpPos, config);
    }
}
