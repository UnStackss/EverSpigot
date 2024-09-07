package net.minecraft.world.level.levelgen.structure.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

public class OceanRuinStructure extends Structure {
    public static final MapCodec<OceanRuinStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    settingsCodec(instance),
                    OceanRuinStructure.Type.CODEC.fieldOf("biome_temp").forGetter(structure -> structure.biomeTemp),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("large_probability").forGetter(structure -> structure.largeProbability),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("cluster_probability").forGetter(structure -> structure.clusterProbability)
                )
                .apply(instance, OceanRuinStructure::new)
    );
    public final OceanRuinStructure.Type biomeTemp;
    public final float largeProbability;
    public final float clusterProbability;

    public OceanRuinStructure(Structure.StructureSettings config, OceanRuinStructure.Type biomeTemperature, float largeProbability, float clusterProbability) {
        super(config);
        this.biomeTemp = biomeTemperature;
        this.largeProbability = largeProbability;
        this.clusterProbability = clusterProbability;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.OCEAN_FLOOR_WG, collector -> this.generatePieces(collector, context));
    }

    private void generatePieces(StructurePiecesBuilder collector, Structure.GenerationContext context) {
        BlockPos blockPos = new BlockPos(context.chunkPos().getMinBlockX(), 90, context.chunkPos().getMinBlockZ());
        Rotation rotation = Rotation.getRandom(context.random());
        OceanRuinPieces.addPieces(context.structureTemplateManager(), blockPos, rotation, collector, context.random(), this);
    }

    @Override
    public StructureType<?> type() {
        return StructureType.OCEAN_RUIN;
    }

    public static enum Type implements StringRepresentable {
        WARM("warm"),
        COLD("cold");

        public static final Codec<OceanRuinStructure.Type> CODEC = StringRepresentable.fromEnum(OceanRuinStructure.Type::values);
        private final String name;

        private Type(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
