package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class GeodeBlockSettings {
    public final BlockStateProvider fillingProvider;
    public final BlockStateProvider innerLayerProvider;
    public final BlockStateProvider alternateInnerLayerProvider;
    public final BlockStateProvider middleLayerProvider;
    public final BlockStateProvider outerLayerProvider;
    public final List<BlockState> innerPlacements;
    public final TagKey<Block> cannotReplace;
    public final TagKey<Block> invalidBlocks;
    public static final Codec<GeodeBlockSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    BlockStateProvider.CODEC.fieldOf("filling_provider").forGetter(config -> config.fillingProvider),
                    BlockStateProvider.CODEC.fieldOf("inner_layer_provider").forGetter(config -> config.innerLayerProvider),
                    BlockStateProvider.CODEC.fieldOf("alternate_inner_layer_provider").forGetter(config -> config.alternateInnerLayerProvider),
                    BlockStateProvider.CODEC.fieldOf("middle_layer_provider").forGetter(config -> config.middleLayerProvider),
                    BlockStateProvider.CODEC.fieldOf("outer_layer_provider").forGetter(config -> config.outerLayerProvider),
                    ExtraCodecs.nonEmptyList(BlockState.CODEC.listOf()).fieldOf("inner_placements").forGetter(config -> config.innerPlacements),
                    TagKey.hashedCodec(Registries.BLOCK).fieldOf("cannot_replace").forGetter(config -> config.cannotReplace),
                    TagKey.hashedCodec(Registries.BLOCK).fieldOf("invalid_blocks").forGetter(config -> config.invalidBlocks)
                )
                .apply(instance, GeodeBlockSettings::new)
    );

    public GeodeBlockSettings(
        BlockStateProvider fillingProvider,
        BlockStateProvider innerLayerProvider,
        BlockStateProvider alternateInnerLayerProvider,
        BlockStateProvider middleLayerProvider,
        BlockStateProvider outerLayerProvider,
        List<BlockState> innerBlocks,
        TagKey<Block> cannotReplace,
        TagKey<Block> invalidBlocks
    ) {
        this.fillingProvider = fillingProvider;
        this.innerLayerProvider = innerLayerProvider;
        this.alternateInnerLayerProvider = alternateInnerLayerProvider;
        this.middleLayerProvider = middleLayerProvider;
        this.outerLayerProvider = outerLayerProvider;
        this.innerPlacements = innerBlocks;
        this.cannotReplace = cannotReplace;
        this.invalidBlocks = invalidBlocks;
    }
}
