package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class NetherFossilStructure extends Structure {
    public static final MapCodec<NetherFossilStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(settingsCodec(instance), HeightProvider.CODEC.fieldOf("height").forGetter(structure -> structure.height))
                .apply(instance, NetherFossilStructure::new)
    );
    public final HeightProvider height;

    public NetherFossilStructure(Structure.StructureSettings config, HeightProvider height) {
        super(config);
        this.height = height;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        WorldgenRandom worldgenRandom = context.random();
        int i = context.chunkPos().getMinBlockX() + worldgenRandom.nextInt(16);
        int j = context.chunkPos().getMinBlockZ() + worldgenRandom.nextInt(16);
        int k = context.chunkGenerator().getSeaLevel();
        WorldGenerationContext worldGenerationContext = new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor());
        int l = this.height.sample(worldgenRandom, worldGenerationContext);
        NoiseColumn noiseColumn = context.chunkGenerator().getBaseColumn(i, j, context.heightAccessor(), context.randomState());
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(i, l, j);

        while (l > k) {
            BlockState blockState = noiseColumn.getBlock(l);
            BlockState blockState2 = noiseColumn.getBlock(--l);
            if (blockState.isAir()
                && (blockState2.is(Blocks.SOUL_SAND) || blockState2.isFaceSturdy(EmptyBlockGetter.INSTANCE, mutableBlockPos.setY(l), Direction.UP))) {
                break;
            }
        }

        if (l <= k) {
            return Optional.empty();
        } else {
            BlockPos blockPos = new BlockPos(i, l, j);
            return Optional.of(
                new Structure.GenerationStub(
                    blockPos, holder -> NetherFossilPieces.addPieces(context.structureTemplateManager(), holder, worldgenRandom, blockPos)
                )
            );
        }
    }

    @Override
    public StructureType<?> type() {
        return StructureType.NETHER_FOSSIL;
    }
}
