package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class WeatheringCopperDoorBlock extends DoorBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperDoorBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    BlockSetType.CODEC.fieldOf("block_set_type").forGetter(DoorBlock::type),
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperDoorBlock::getAge),
                    propertiesCodec()
                )
                .apply(instance, WeatheringCopperDoorBlock::new)
    );
    private final WeatheringCopper.WeatherState weatherState;

    @Override
    public MapCodec<WeatheringCopperDoorBlock> codec() {
        return CODEC;
    }

    protected WeatheringCopperDoorBlock(BlockSetType type, WeatheringCopper.WeatherState oxidationLevel, BlockBehaviour.Properties settings) {
        super(type, settings);
        this.weatherState = oxidationLevel;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            this.changeOverTime(state, world, pos, random);
        }
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
