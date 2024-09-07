package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ColoredFallingBlock extends FallingBlock {
    public static final MapCodec<ColoredFallingBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ColorRGBA.CODEC.fieldOf("falling_dust_color").forGetter(block -> block.dustColor), propertiesCodec())
                .apply(instance, ColoredFallingBlock::new)
    );
    private final ColorRGBA dustColor;

    @Override
    public MapCodec<ColoredFallingBlock> codec() {
        return CODEC;
    }

    public ColoredFallingBlock(ColorRGBA color, BlockBehaviour.Properties settings) {
        super(settings);
        this.dustColor = color;
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter world, BlockPos pos) {
        return this.dustColor.rgba();
    }
}
