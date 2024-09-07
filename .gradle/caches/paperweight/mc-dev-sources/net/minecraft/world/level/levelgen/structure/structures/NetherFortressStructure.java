package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class NetherFortressStructure extends Structure {
    public static final WeightedRandomList<MobSpawnSettings.SpawnerData> FORTRESS_ENEMIES = WeightedRandomList.create(
        new MobSpawnSettings.SpawnerData(EntityType.BLAZE, 10, 2, 3),
        new MobSpawnSettings.SpawnerData(EntityType.ZOMBIFIED_PIGLIN, 5, 4, 4),
        new MobSpawnSettings.SpawnerData(EntityType.WITHER_SKELETON, 8, 5, 5),
        new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 2, 5, 5),
        new MobSpawnSettings.SpawnerData(EntityType.MAGMA_CUBE, 3, 4, 4)
    );
    public static final MapCodec<NetherFortressStructure> CODEC = simpleCodec(NetherFortressStructure::new);

    public NetherFortressStructure(Structure.StructureSettings config) {
        super(config);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), 64, chunkPos.getMinBlockZ());
        return Optional.of(new Structure.GenerationStub(blockPos, collector -> generatePieces(collector, context)));
    }

    private static void generatePieces(StructurePiecesBuilder collector, Structure.GenerationContext context) {
        NetherFortressPieces.StartPiece startPiece = new NetherFortressPieces.StartPiece(
            context.random(), context.chunkPos().getBlockX(2), context.chunkPos().getBlockZ(2)
        );
        collector.addPiece(startPiece);
        startPiece.addChildren(startPiece, collector, context.random());
        List<StructurePiece> list = startPiece.pendingChildren;

        while (!list.isEmpty()) {
            int i = context.random().nextInt(list.size());
            StructurePiece structurePiece = list.remove(i);
            structurePiece.addChildren(startPiece, collector, context.random());
        }

        collector.moveInsideHeights(context.random(), 48, 70);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.FORTRESS;
    }
}
