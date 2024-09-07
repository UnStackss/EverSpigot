package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class EndCityStructure extends Structure {
    public static final MapCodec<EndCityStructure> CODEC = simpleCodec(EndCityStructure::new);

    public EndCityStructure(Structure.StructureSettings config) {
        super(config);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockPos = this.getLowestYIn5by5BoxOffset7Blocks(context, rotation);
        return blockPos.getY() < 60
            ? Optional.empty()
            : Optional.of(new Structure.GenerationStub(blockPos, collector -> this.generatePieces(collector, blockPos, rotation, context)));
    }

    private void generatePieces(StructurePiecesBuilder collector, BlockPos pos, Rotation rotation, Structure.GenerationContext context) {
        List<StructurePiece> list = Lists.newArrayList();
        EndCityPieces.startHouseTower(context.structureTemplateManager(), pos, rotation, list, context.random());
        list.forEach(collector::addPiece);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.END_CITY;
    }
}
