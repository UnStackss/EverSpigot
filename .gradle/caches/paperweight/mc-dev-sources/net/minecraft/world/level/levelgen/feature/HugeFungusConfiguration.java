package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class HugeFungusConfiguration implements FeatureConfiguration {
    public static final Codec<HugeFungusConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    BlockState.CODEC.fieldOf("valid_base_block").forGetter(config -> config.validBaseState),
                    BlockState.CODEC.fieldOf("stem_state").forGetter(config -> config.stemState),
                    BlockState.CODEC.fieldOf("hat_state").forGetter(config -> config.hatState),
                    BlockState.CODEC.fieldOf("decor_state").forGetter(config -> config.decorState),
                    BlockPredicate.CODEC.fieldOf("replaceable_blocks").forGetter(config -> config.replaceableBlocks),
                    Codec.BOOL.fieldOf("planted").orElse(false).forGetter(config -> config.planted)
                )
                .apply(instance, HugeFungusConfiguration::new)
    );
    public final BlockState validBaseState;
    public final BlockState stemState;
    public final BlockState hatState;
    public final BlockState decorState;
    public final BlockPredicate replaceableBlocks;
    public final boolean planted;

    public HugeFungusConfiguration(
        BlockState validBaseBlock, BlockState stemState, BlockState hatState, BlockState decorationState, BlockPredicate replaceableBlocks, boolean planted
    ) {
        this.validBaseState = validBaseBlock;
        this.stemState = stemState;
        this.hatState = hatState;
        this.decorState = decorationState;
        this.replaceableBlocks = replaceableBlocks;
        this.planted = planted;
    }
}
