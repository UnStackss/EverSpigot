package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;

public class ProtectedBlockProcessor extends StructureProcessor {
    public final TagKey<Block> cannotReplace;
    public static final MapCodec<ProtectedBlockProcessor> CODEC = TagKey.hashedCodec(Registries.BLOCK)
        .xmap(ProtectedBlockProcessor::new, processor -> processor.cannotReplace)
        .fieldOf("value");

    public ProtectedBlockProcessor(TagKey<Block> protectedBlocksTag) {
        this.cannotReplace = protectedBlocksTag;
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
        LevelReader world,
        BlockPos pos,
        BlockPos pivot,
        StructureTemplate.StructureBlockInfo originalBlockInfo,
        StructureTemplate.StructureBlockInfo currentBlockInfo,
        StructurePlaceSettings data
    ) {
        return Feature.isReplaceable(this.cannotReplace).test(world.getBlockState(currentBlockInfo.pos())) ? currentBlockInfo : null;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.PROTECTED_BLOCKS;
    }
}
