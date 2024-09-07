package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class MineshaftStructure extends Structure {
    public static final MapCodec<MineshaftStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    settingsCodec(instance), MineshaftStructure.Type.CODEC.fieldOf("mineshaft_type").forGetter(mineshaftStructure -> mineshaftStructure.type)
                )
                .apply(instance, MineshaftStructure::new)
    );
    private final MineshaftStructure.Type type;

    public MineshaftStructure(Structure.StructureSettings config, MineshaftStructure.Type type) {
        super(config);
        this.type = type;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        context.random().nextDouble();
        ChunkPos chunkPos = context.chunkPos();
        BlockPos blockPos = new BlockPos(chunkPos.getMiddleBlockX(), 50, chunkPos.getMinBlockZ());
        StructurePiecesBuilder structurePiecesBuilder = new StructurePiecesBuilder();
        int i = this.generatePiecesAndAdjust(structurePiecesBuilder, context);
        return Optional.of(new Structure.GenerationStub(blockPos.offset(0, i, 0), Either.right(structurePiecesBuilder)));
    }

    private int generatePiecesAndAdjust(StructurePiecesBuilder collector, Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        WorldgenRandom worldgenRandom = context.random();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        MineshaftPieces.MineShaftRoom mineShaftRoom = new MineshaftPieces.MineShaftRoom(
            0, worldgenRandom, chunkPos.getBlockX(2), chunkPos.getBlockZ(2), this.type
        );
        collector.addPiece(mineShaftRoom);
        mineShaftRoom.addChildren(mineShaftRoom, collector, worldgenRandom);
        int i = chunkGenerator.getSeaLevel();
        if (this.type == MineshaftStructure.Type.MESA) {
            BlockPos blockPos = collector.getBoundingBox().getCenter();
            int j = chunkGenerator.getBaseHeight(
                blockPos.getX(), blockPos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()
            );
            int k = j <= i ? i : Mth.randomBetweenInclusive(worldgenRandom, i, j);
            int l = k - blockPos.getY();
            collector.offsetPiecesVertically(l);
            return l;
        } else {
            return collector.moveBelowSeaLevel(i, chunkGenerator.getMinY(), worldgenRandom, 10);
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.MINESHAFT;
    }

    public static enum Type implements StringRepresentable {
        NORMAL("normal", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_FENCE),
        MESA("mesa", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE);

        public static final Codec<MineshaftStructure.Type> CODEC = StringRepresentable.fromEnum(MineshaftStructure.Type::values);
        private static final IntFunction<MineshaftStructure.Type> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        private final String name;
        private final BlockState woodState;
        private final BlockState planksState;
        private final BlockState fenceState;

        private Type(final String name, final Block log, final Block planks, final Block fence) {
            this.name = name;
            this.woodState = log.defaultBlockState();
            this.planksState = planks.defaultBlockState();
            this.fenceState = fence.defaultBlockState();
        }

        public String getName() {
            return this.name;
        }

        public static MineshaftStructure.Type byId(int id) {
            return BY_ID.apply(id);
        }

        public BlockState getWoodState() {
            return this.woodState;
        }

        public BlockState getPlanksState() {
            return this.planksState;
        }

        public BlockState getFenceState() {
            return this.fenceState;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
