package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class WeatheringCopperBulbBlock extends CopperBulbBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperBulbBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperBulbBlock::getAge), propertiesCodec()
                )
                .apply(instance, WeatheringCopperBulbBlock::new)
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    protected MapCodec<WeatheringCopperBulbBlock> codec() {
        return CODEC;
    }

    public WeatheringCopperBulbBlock(WeatheringCopper.WeatherState oxidationLevel, BlockBehaviour.Properties settings) {
        super(settings);
        this.weatherState = oxidationLevel;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.changeOverTime(state, world, pos, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    public WeatheringCopper.WeatherState getAge() {
        return this.weatherState;
    }
}
