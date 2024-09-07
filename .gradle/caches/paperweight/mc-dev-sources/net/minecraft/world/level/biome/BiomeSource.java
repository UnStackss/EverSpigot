package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;

public abstract class BiomeSource implements BiomeResolver {
    public static final Codec<BiomeSource> CODEC = BuiltInRegistries.BIOME_SOURCE.byNameCodec().dispatchStable(BiomeSource::codec, Function.identity());
    private final Supplier<Set<Holder<Biome>>> possibleBiomes = Suppliers.memoize(
        () -> this.collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet())
    );

    protected BiomeSource() {
    }

    protected abstract MapCodec<? extends BiomeSource> codec();

    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    public Set<Holder<Biome>> possibleBiomes() {
        return this.possibleBiomes.get();
    }

    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        int i = QuartPos.fromBlock(x - radius);
        int j = QuartPos.fromBlock(y - radius);
        int k = QuartPos.fromBlock(z - radius);
        int l = QuartPos.fromBlock(x + radius);
        int m = QuartPos.fromBlock(y + radius);
        int n = QuartPos.fromBlock(z + radius);
        int o = l - i + 1;
        int p = m - j + 1;
        int q = n - k + 1;
        Set<Holder<Biome>> set = Sets.newHashSet();

        for (int r = 0; r < q; r++) {
            for (int s = 0; s < o; s++) {
                for (int t = 0; t < p; t++) {
                    int u = i + s;
                    int v = j + t;
                    int w = k + r;
                    set.add(this.getNoiseBiome(u, v, w, sampler));
                }
            }
        }

        return set;
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x, int y, int z, int radius, Predicate<Holder<Biome>> predicate, RandomSource random, Climate.Sampler noiseSampler
    ) {
        return this.findBiomeHorizontal(x, y, z, radius, 1, predicate, random, false, noiseSampler);
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        BlockPos origin,
        int radius,
        int horizontalBlockCheckInterval,
        int verticalBlockCheckInterval,
        Predicate<Holder<Biome>> predicate,
        Climate.Sampler noiseSampler,
        LevelReader world
    ) {
        Set<Holder<Biome>> set = this.possibleBiomes().stream().filter(predicate).collect(Collectors.toUnmodifiableSet());
        if (set.isEmpty()) {
            return null;
        } else {
            int i = Math.floorDiv(radius, horizontalBlockCheckInterval);
            int[] is = Mth.outFromOrigin(origin.getY(), world.getMinBuildHeight() + 1, world.getMaxBuildHeight(), verticalBlockCheckInterval).toArray();

            for (BlockPos.MutableBlockPos mutableBlockPos : BlockPos.spiralAround(BlockPos.ZERO, i, Direction.EAST, Direction.SOUTH)) {
                int j = origin.getX() + mutableBlockPos.getX() * horizontalBlockCheckInterval;
                int k = origin.getZ() + mutableBlockPos.getZ() * horizontalBlockCheckInterval;
                int l = QuartPos.fromBlock(j);
                int m = QuartPos.fromBlock(k);

                for (int n : is) {
                    int o = QuartPos.fromBlock(n);
                    Holder<Biome> holder = this.getNoiseBiome(l, o, m, noiseSampler);
                    if (set.contains(holder)) {
                        return Pair.of(new BlockPos(j, n, k), holder);
                    }
                }
            }

            return null;
        }
    }

    @Nullable
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
        int i = QuartPos.fromBlock(x);
        int j = QuartPos.fromBlock(z);
        int k = QuartPos.fromBlock(radius);
        int l = QuartPos.fromBlock(y);
        Pair<BlockPos, Holder<Biome>> pair = null;
        int m = 0;
        int n = bl ? 0 : k;
        int o = n;

        while (o <= k) {
            for (int p = SharedConstants.debugGenerateSquareTerrainWithoutNoise ? 0 : -o; p <= o; p += blockCheckInterval) {
                boolean bl2 = Math.abs(p) == o;

                for (int q = -o; q <= o; q += blockCheckInterval) {
                    if (bl) {
                        boolean bl3 = Math.abs(q) == o;
                        if (!bl3 && !bl2) {
                            continue;
                        }
                    }

                    int r = i + q;
                    int s = j + p;
                    Holder<Biome> holder = this.getNoiseBiome(r, l, s, noiseSampler);
                    if (predicate.test(holder)) {
                        if (pair == null || random.nextInt(m + 1) == 0) {
                            BlockPos blockPos = new BlockPos(QuartPos.toBlock(r), y, QuartPos.toBlock(s));
                            if (bl) {
                                return Pair.of(blockPos, holder);
                            }

                            pair = Pair.of(blockPos, holder);
                        }

                        m++;
                    }
                }
            }

            o += blockCheckInterval;
        }

        return pair;
    }

    @Override
    public abstract Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise);

    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler noiseSampler) {
    }
}
