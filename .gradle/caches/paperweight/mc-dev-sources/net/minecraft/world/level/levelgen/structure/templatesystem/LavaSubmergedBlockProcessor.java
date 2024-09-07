package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class LavaSubmergedBlockProcessor extends StructureProcessor {
    public static final MapCodec<LavaSubmergedBlockProcessor> CODEC = MapCodec.unit(() -> LavaSubmergedBlockProcessor.INSTANCE);
    public static final LavaSubmergedBlockProcessor INSTANCE = new LavaSubmergedBlockProcessor();

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
        BlockPos blockPos = currentBlockInfo.pos();
        boolean bl = world.getBlockState(blockPos).is(Blocks.LAVA);
        return bl && !Block.isShapeFullBlock(currentBlockInfo.state().getShape(world, blockPos))
            ? new StructureTemplate.StructureBlockInfo(blockPos, Blocks.LAVA.defaultBlockState(), currentBlockInfo.nbt())
            : currentBlockInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.LAVA_SUBMERGED_BLOCK;
    }
}
