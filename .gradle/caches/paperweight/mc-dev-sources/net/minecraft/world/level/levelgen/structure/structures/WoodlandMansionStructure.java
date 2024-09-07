package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class WoodlandMansionStructure extends Structure {
    public static final MapCodec<WoodlandMansionStructure> CODEC = simpleCodec(WoodlandMansionStructure::new);

    public WoodlandMansionStructure(Structure.StructureSettings config) {
        super(config);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockPos = this.getLowestYIn5by5BoxOffset7Blocks(context, rotation);
        return blockPos.getY() < 60
            ? Optional.empty()
            : Optional.of(new Structure.GenerationStub(blockPos, collector -> this.generatePieces(collector, context, blockPos, rotation)));
    }

    private void generatePieces(StructurePiecesBuilder collector, Structure.GenerationContext context, BlockPos pos, Rotation rotation) {
        List<WoodlandMansionPieces.WoodlandMansionPiece> list = Lists.newLinkedList();
        WoodlandMansionPieces.generateMansion(context.structureTemplateManager(), pos, rotation, list, context.random());
        list.forEach(collector::addPiece);
    }

    @Override
    public void afterPlace(
        WorldGenLevel world,
        StructureManager structureAccessor,
        ChunkGenerator chunkGenerator,
        RandomSource random,
        BoundingBox box,
        ChunkPos chunkPos,
        PiecesContainer pieces
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int i = world.getMinBuildHeight();
        BoundingBox boundingBox = pieces.calculateBoundingBox();
        int j = boundingBox.minY();

        for (int k = box.minX(); k <= box.maxX(); k++) {
            for (int l = box.minZ(); l <= box.maxZ(); l++) {
                mutableBlockPos.set(k, j, l);
                if (!world.isEmptyBlock(mutableBlockPos) && boundingBox.isInside(mutableBlockPos) && pieces.isInsidePiece(mutableBlockPos)) {
                    for (int m = j - 1; m > i; m--) {
                        mutableBlockPos.setY(m);
                        if (!world.isEmptyBlock(mutableBlockPos) && !world.getBlockState(mutableBlockPos).liquid()) {
                            break;
                        }

                        world.setBlock(mutableBlockPos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.WOODLAND_MANSION;
    }
}
