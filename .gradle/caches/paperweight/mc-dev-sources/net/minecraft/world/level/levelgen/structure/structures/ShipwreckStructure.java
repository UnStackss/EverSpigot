package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class ShipwreckStructure extends Structure {
    public static final MapCodec<ShipwreckStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(settingsCodec(instance), Codec.BOOL.fieldOf("is_beached").forGetter(shipwreckStructure -> shipwreckStructure.isBeached))
                .apply(instance, ShipwreckStructure::new)
    );
    public final boolean isBeached;

    public ShipwreckStructure(Structure.StructureSettings config, boolean beached) {
        super(config);
        this.isBeached = beached;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        Heightmap.Types types = this.isBeached ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.OCEAN_FLOOR_WG;
        return onTopOfChunkCenter(context, types, collector -> this.generatePieces(collector, context));
    }

    private void generatePieces(StructurePiecesBuilder collector, Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        BlockPos blockPos = new BlockPos(context.chunkPos().getMinBlockX(), 90, context.chunkPos().getMinBlockZ());
        ShipwreckPieces.ShipwreckPiece shipwreckPiece = ShipwreckPieces.addRandomPiece(
            context.structureTemplateManager(), blockPos, rotation, collector, context.random(), this.isBeached
        );
        if (shipwreckPiece.isTooBigToFitInWorldGenRegion()) {
            BoundingBox boundingBox = shipwreckPiece.getBoundingBox();
            int j;
            if (this.isBeached) {
                int i = Structure.getLowestY(context, boundingBox.minX(), boundingBox.getXSpan(), boundingBox.minZ(), boundingBox.getZSpan());
                j = shipwreckPiece.calculateBeachedPosition(i, context.random());
            } else {
                j = Structure.getMeanFirstOccupiedHeight(context, boundingBox.minX(), boundingBox.getXSpan(), boundingBox.minZ(), boundingBox.getZSpan());
            }

            shipwreckPiece.adjustPositionHeight(j);
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.SHIPWRECK;
    }
}
