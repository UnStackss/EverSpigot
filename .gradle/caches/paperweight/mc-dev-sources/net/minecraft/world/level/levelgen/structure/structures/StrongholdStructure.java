package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class StrongholdStructure extends Structure {
    public static final MapCodec<StrongholdStructure> CODEC = simpleCodec(StrongholdStructure::new);

    public StrongholdStructure(Structure.StructureSettings config) {
        super(config);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return Optional.of(new Structure.GenerationStub(context.chunkPos().getWorldPosition(), collector -> generatePieces(collector, context)));
    }

    private static void generatePieces(StructurePiecesBuilder collector, Structure.GenerationContext context) {
        int i = 0;

        StrongholdPieces.StartPiece startPiece;
        do {
            collector.clear();
            context.random().setLargeFeatureSeed(context.seed() + (long)(i++), context.chunkPos().x, context.chunkPos().z);
            StrongholdPieces.resetPieces();
            startPiece = new StrongholdPieces.StartPiece(context.random(), context.chunkPos().getBlockX(2), context.chunkPos().getBlockZ(2));
            collector.addPiece(startPiece);
            startPiece.addChildren(startPiece, collector, context.random());
            List<StructurePiece> list = startPiece.pendingChildren;

            while (!list.isEmpty()) {
                int j = context.random().nextInt(list.size());
                StructurePiece structurePiece = list.remove(j);
                structurePiece.addChildren(startPiece, collector, context.random());
            }

            collector.moveBelowSeaLevel(context.chunkGenerator().getSeaLevel(), context.chunkGenerator().getMinY(), context.random(), 10);
        } while (collector.isEmpty() || startPiece.portalRoomPiece == null);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.STRONGHOLD;
    }
}
