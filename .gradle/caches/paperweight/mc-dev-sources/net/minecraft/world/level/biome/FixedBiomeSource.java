package net.minecraft.world.level.biome;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;

public class FixedBiomeSource extends BiomeSource implements BiomeManager.NoiseBiomeSource {
    public static final MapCodec<FixedBiomeSource> CODEC = Biome.CODEC.fieldOf("biome").xmap(FixedBiomeSource::new, biomeSource -> biomeSource.biome).stable();
    private final Holder<Biome> biome;

    public FixedBiomeSource(Holder<Biome> biome) {
        this.biome = biome;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(this.biome);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
        return this.biome;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        return this.biome;
    }

    @Nullable
    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x,
        int y,
        int z,
        int radius,
        int blockCheckInterval,
        Predicate<Holder<Biome>> predicate,
        RandomSource random,
        boolean bl,
        Climate.Sampler noiseSampler
    ) {
        if (predicate.test(this.biome)) {
            return bl
                ? Pair.of(new BlockPos(x, y, z), this.biome)
                : Pair.of(new BlockPos(x - radius + random.nextInt(radius * 2 + 1), y, z - radius + random.nextInt(radius * 2 + 1)), this.biome);
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        BlockPos origin,
        int radius,
        int horizontalBlockCheckInterval,
        int verticalBlockCheckInterval,
        Predicate<Holder<Biome>> predicate,
        Climate.Sampler noiseSampler,
        LevelReader world
    ) {
        return predicate.test(this.biome) ? Pair.of(origin, this.biome) : null;
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        return Sets.newHashSet(Set.of(this.biome));
    }
}
