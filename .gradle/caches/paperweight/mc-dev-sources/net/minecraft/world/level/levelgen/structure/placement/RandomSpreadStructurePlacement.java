package net.minecraft.world.level.levelgen.structure.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public class RandomSpreadStructurePlacement extends StructurePlacement {
    public static final MapCodec<RandomSpreadStructurePlacement> CODEC = RecordCodecBuilder.<RandomSpreadStructurePlacement>mapCodec(
            instance -> placementCodec(instance)
                    .and(
                        instance.group(
                            Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                            Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                            RandomSpreadType.CODEC
                                .optionalFieldOf("spread_type", RandomSpreadType.LINEAR)
                                .forGetter(RandomSpreadStructurePlacement::spreadType)
                        )
                    )
                    .apply(instance, RandomSpreadStructurePlacement::new)
        )
        .validate(RandomSpreadStructurePlacement::validate);
    private final int spacing;
    private final int separation;
    private final RandomSpreadType spreadType;

    private static DataResult<RandomSpreadStructurePlacement> validate(RandomSpreadStructurePlacement structurePlacement) {
        return structurePlacement.spacing <= structurePlacement.separation
            ? DataResult.error(() -> "Spacing has to be larger than separation")
            : DataResult.success(structurePlacement);
    }

    public RandomSpreadStructurePlacement(
        Vec3i locateOffset,
        StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
        float frequency,
        int salt,
        Optional<StructurePlacement.ExclusionZone> exclusionZone,
        int spacing,
        int separation,
        RandomSpreadType spreadType
    ) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.spacing = spacing;
        this.separation = separation;
        this.spreadType = spreadType;
    }

    public RandomSpreadStructurePlacement(int spacing, int separation, RandomSpreadType spreadType, int salt) {
        this(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), spacing, separation, spreadType);
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public RandomSpreadType spreadType() {
        return this.spreadType;
    }

    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        int i = Math.floorDiv(chunkX, this.spacing);
        int j = Math.floorDiv(chunkZ, this.spacing);
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        worldgenRandom.setLargeFeatureWithSalt(seed, i, j, this.salt());
        int k = this.spacing - this.separation;
        int l = this.spreadType.evaluate(worldgenRandom, k);
        int m = this.spreadType.evaluate(worldgenRandom, k);
        return new ChunkPos(i * this.spacing + l, j * this.spacing + m);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState calculator, int chunkX, int chunkZ) {
        ChunkPos chunkPos = this.getPotentialStructureChunk(calculator.getLevelSeed(), chunkX, chunkZ);
        return chunkPos.x == chunkX && chunkPos.z == chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return StructurePlacementType.RANDOM_SPREAD;
    }
}
