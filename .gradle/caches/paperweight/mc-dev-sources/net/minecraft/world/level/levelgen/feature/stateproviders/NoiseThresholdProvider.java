package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseThresholdProvider extends NoiseBasedStateProvider {
    public static final MapCodec<NoiseThresholdProvider> CODEC = RecordCodecBuilder.mapCodec(
        instance -> noiseCodec(instance)
                .and(
                    instance.group(
                        Codec.floatRange(-1.0F, 1.0F).fieldOf("threshold").forGetter(noiseThresholdProvider -> noiseThresholdProvider.threshold),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("high_chance").forGetter(noiseThresholdProvider -> noiseThresholdProvider.highChance),
                        BlockState.CODEC.fieldOf("default_state").forGetter(noiseThresholdProvider -> noiseThresholdProvider.defaultState),
                        Codec.list(BlockState.CODEC).fieldOf("low_states").forGetter(noiseThresholdProvider -> noiseThresholdProvider.lowStates),
                        Codec.list(BlockState.CODEC).fieldOf("high_states").forGetter(noiseThresholdProvider -> noiseThresholdProvider.highStates)
                    )
                )
                .apply(instance, NoiseThresholdProvider::new)
    );
    private final float threshold;
    private final float highChance;
    private final BlockState defaultState;
    private final List<BlockState> lowStates;
    private final List<BlockState> highStates;

    public NoiseThresholdProvider(
        long seed,
        NormalNoise.NoiseParameters noiseParameters,
        float scale,
        float threshold,
        float highChance,
        BlockState defaultState,
        List<BlockState> lowStates,
        List<BlockState> highStates
    ) {
        super(seed, noiseParameters, scale);
        this.threshold = threshold;
        this.highChance = highChance;
        this.defaultState = defaultState;
        this.lowStates = lowStates;
        this.highStates = highStates;
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.NOISE_THRESHOLD_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        double d = this.getNoiseValue(pos, (double)this.scale);
        if (d < (double)this.threshold) {
            return Util.getRandom(this.lowStates, random);
        } else {
            return random.nextFloat() < this.highChance ? Util.getRandom(this.highStates, random) : this.defaultState;
        }
    }
}
