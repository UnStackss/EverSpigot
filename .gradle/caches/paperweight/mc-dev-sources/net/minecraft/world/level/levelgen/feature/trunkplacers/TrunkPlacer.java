package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public abstract class TrunkPlacer {
    public static final Codec<TrunkPlacer> CODEC = BuiltInRegistries.TRUNK_PLACER_TYPE.byNameCodec().dispatch(TrunkPlacer::type, TrunkPlacerType::codec);
    private static final int MAX_BASE_HEIGHT = 32;
    private static final int MAX_RAND = 24;
    public static final int MAX_HEIGHT = 80;
    protected final int baseHeight;
    protected final int heightRandA;
    protected final int heightRandB;

    protected static <P extends TrunkPlacer> P3<Mu<P>, Integer, Integer, Integer> trunkPlacerParts(Instance<P> instance) {
        return instance.group(
            Codec.intRange(0, 32).fieldOf("base_height").forGetter(placer -> placer.baseHeight),
            Codec.intRange(0, 24).fieldOf("height_rand_a").forGetter(placer -> placer.heightRandA),
            Codec.intRange(0, 24).fieldOf("height_rand_b").forGetter(placer -> placer.heightRandB)
        );
    }

    public TrunkPlacer(int baseHeight, int firstRandomHeight, int secondRandomHeight) {
        this.baseHeight = baseHeight;
        this.heightRandA = firstRandomHeight;
        this.heightRandB = secondRandomHeight;
    }

    protected abstract TrunkPlacerType<?> type();

    public abstract List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, int height, BlockPos startPos, TreeConfiguration config
    );

    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }

    private static boolean isDirt(LevelSimulatedReader world, BlockPos pos) {
        return world.isStateAtPosition(pos, state -> Feature.isDirt(state) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.MYCELIUM));
    }

    protected static void setDirtAt(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos pos, TreeConfiguration config
    ) {
        if (config.forceDirt || !isDirt(world, pos)) {
            replacer.accept(pos, config.dirtProvider.getState(random, pos));
        }
    }

    protected boolean placeLog(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos pos, TreeConfiguration config
    ) {
        return this.placeLog(world, replacer, random, pos, config, Function.identity());
    }

    protected boolean placeLog(
        LevelSimulatedReader world,
        BiConsumer<BlockPos, BlockState> replacer,
        RandomSource random,
        BlockPos pos,
        TreeConfiguration config,
        Function<BlockState, BlockState> function
    ) {
        if (this.validTreePos(world, pos)) {
            replacer.accept(pos, function.apply(config.trunkProvider.getState(random, pos)));
            return true;
        } else {
            return false;
        }
    }

    protected void placeLogIfFree(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos.MutableBlockPos pos, TreeConfiguration config
    ) {
        if (this.isFree(world, pos)) {
            this.placeLog(world, replacer, random, pos, config);
        }
    }

    protected boolean validTreePos(LevelSimulatedReader world, BlockPos pos) {
        return TreeFeature.validTreePos(world, pos);
    }

    public boolean isFree(LevelSimulatedReader world, BlockPos pos) {
        return this.validTreePos(world, pos) || world.isStateAtPosition(pos, state -> state.is(BlockTags.LOGS));
    }
}
