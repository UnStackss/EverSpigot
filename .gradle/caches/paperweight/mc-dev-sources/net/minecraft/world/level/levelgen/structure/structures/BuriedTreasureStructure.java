package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class BuriedTreasureStructure extends Structure {
    public static final MapCodec<BuriedTreasureStructure> CODEC = simpleCodec(BuriedTreasureStructure::new);

    public BuriedTreasureStructure(Structure.StructureSettings config) {
        super(config);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.OCEAN_FLOOR_WG, collector -> generatePieces(collector, context));
    }

    private static void generatePieces(StructurePiecesBuilder collector, Structure.GenerationContext context) {
        BlockPos blockPos = new BlockPos(context.chunkPos().getBlockX(9), 90, context.chunkPos().getBlockZ(9));
        collector.addPiece(new BuriedTreasurePieces.BuriedTreasurePiece(blockPos));
    }

    @Override
    public StructureType<?> type() {
        return StructureType.BURIED_TREASURE;
    }
}
