package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.state.BlockState;

public class WeightedStateProvider extends BlockStateProvider {
    public static final MapCodec<WeightedStateProvider> CODEC = SimpleWeightedRandomList.wrappedCodec(BlockState.CODEC)
        .comapFlatMap(WeightedStateProvider::create, weightedStateProvider -> weightedStateProvider.weightedList)
        .fieldOf("entries");
    private final SimpleWeightedRandomList<BlockState> weightedList;

    private static DataResult<WeightedStateProvider> create(SimpleWeightedRandomList<BlockState> states) {
        return states.isEmpty() ? DataResult.error(() -> "WeightedStateProvider with no states") : DataResult.success(new WeightedStateProvider(states));
    }

    public WeightedStateProvider(SimpleWeightedRandomList<BlockState> states) {
        this.weightedList = states;
    }

    public WeightedStateProvider(SimpleWeightedRandomList.Builder<BlockState> states) {
        this(states.build());
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.WEIGHTED_STATE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        return this.weightedList.getRandomValue(random).orElseThrow(IllegalStateException::new);
    }
}
