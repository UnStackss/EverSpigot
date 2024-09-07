package net.minecraft.world.level.biome;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public class MultiNoiseBiomeSource extends BiomeSource {
    private static final MapCodec<Holder<Biome>> ENTRY_CODEC = Biome.CODEC.fieldOf("biome");
    public static final MapCodec<Climate.ParameterList<Holder<Biome>>> DIRECT_CODEC = Climate.ParameterList.codec(ENTRY_CODEC).fieldOf("biomes");
    private static final MapCodec<Holder<MultiNoiseBiomeSourceParameterList>> PRESET_CODEC = MultiNoiseBiomeSourceParameterList.CODEC
        .fieldOf("preset")
        .withLifecycle(Lifecycle.stable());
    public static final MapCodec<MultiNoiseBiomeSource> CODEC = Codec.mapEither(DIRECT_CODEC, PRESET_CODEC)
        .xmap(MultiNoiseBiomeSource::new, source -> source.parameters);
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;

    private MultiNoiseBiomeSource(Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries) {
        this.parameters = biomeEntries;
    }

    public static MultiNoiseBiomeSource createFromList(Climate.ParameterList<Holder<Biome>> biomeEntries) {
        return new MultiNoiseBiomeSource(Either.left(biomeEntries));
    }

    public static MultiNoiseBiomeSource createFromPreset(Holder<MultiNoiseBiomeSourceParameterList> biomeEntries) {
        return new MultiNoiseBiomeSource(Either.right(biomeEntries));
    }

    private Climate.ParameterList<Holder<Biome>> parameters() {
        return this.parameters.map(entries -> entries, parameterListEntry -> parameterListEntry.value().parameters());
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.parameters().values().stream().map(Pair::getSecond);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    public boolean stable(ResourceKey<MultiNoiseBiomeSourceParameterList> parameterList) {
        Optional<Holder<MultiNoiseBiomeSourceParameterList>> optional = this.parameters.right();
        return optional.isPresent() && optional.get().is(parameterList);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
        return this.getNoiseBiome(noise.sample(x, y, z));
    }

    @VisibleForDebug
    public Holder<Biome> getNoiseBiome(Climate.TargetPoint point) {
        return this.parameters().findValue(point);
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler noiseSampler) {
        int i = QuartPos.fromBlock(pos.getX());
        int j = QuartPos.fromBlock(pos.getY());
        int k = QuartPos.fromBlock(pos.getZ());
        Climate.TargetPoint targetPoint = noiseSampler.sample(i, j, k);
        float f = Climate.unquantizeCoord(targetPoint.continentalness());
        float g = Climate.unquantizeCoord(targetPoint.erosion());
        float h = Climate.unquantizeCoord(targetPoint.temperature());
        float l = Climate.unquantizeCoord(targetPoint.humidity());
        float m = Climate.unquantizeCoord(targetPoint.weirdness());
        double d = (double)NoiseRouterData.peaksAndValleys(m);
        OverworldBiomeBuilder overworldBiomeBuilder = new OverworldBiomeBuilder();
        info.add(
            "Biome builder PV: "
                + OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(d)
                + " C: "
                + overworldBiomeBuilder.getDebugStringForContinentalness((double)f)
                + " E: "
                + overworldBiomeBuilder.getDebugStringForErosion((double)g)
                + " T: "
                + overworldBiomeBuilder.getDebugStringForTemperature((double)h)
                + " H: "
                + overworldBiomeBuilder.getDebugStringForHumidity((double)l)
        );
    }
}
