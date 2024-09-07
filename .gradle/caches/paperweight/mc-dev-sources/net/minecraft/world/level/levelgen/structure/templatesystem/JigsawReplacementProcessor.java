package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class JigsawReplacementProcessor extends StructureProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<JigsawReplacementProcessor> CODEC = MapCodec.unit(() -> JigsawReplacementProcessor.INSTANCE);
    public static final JigsawReplacementProcessor INSTANCE = new JigsawReplacementProcessor();

    private JigsawReplacementProcessor() {
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
        BlockState blockState = currentBlockInfo.state();
        if (blockState.is(Blocks.JIGSAW)) {
            if (currentBlockInfo.nbt() == null) {
                LOGGER.warn("Jigsaw block at {} is missing nbt, will not replace", pos);
                return currentBlockInfo;
            } else {
                String string = currentBlockInfo.nbt().getString("final_state");

                BlockState blockState2;
                try {
                    BlockStateParser.BlockResult blockResult = BlockStateParser.parseForBlock(world.holderLookup(Registries.BLOCK), string, true);
                    blockState2 = blockResult.blockState();
                } catch (CommandSyntaxException var11) {
                    LOGGER.error("Failed to parse jigsaw replacement state '{}' at {}: {}", string, pos, var11.getMessage());
                    return null;
                }

                return blockState2.is(Blocks.STRUCTURE_VOID) ? null : new StructureTemplate.StructureBlockInfo(currentBlockInfo.pos(), blockState2, null);
            }
        } else {
            return currentBlockInfo;
        }
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.JIGSAW_REPLACEMENT;
    }
}
