package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class XoroshiroRandomSource implements RandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.110223E-16F;
    public static final Codec<XoroshiroRandomSource> CODEC = Xoroshiro128PlusPlus.CODEC
        .xmap(implementation -> new XoroshiroRandomSource(implementation), random -> random.randomNumberGenerator);
    private Xoroshiro128PlusPlus randomNumberGenerator;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public XoroshiroRandomSource(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandomSource(RandomSupport.Seed128bit seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seed);
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    private XoroshiroRandomSource(Xoroshiro128PlusPlus implementation) {
        this.randomNumberGenerator = implementation;
    }

    @Override
    public RandomSource fork() {
        return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
        this.gaussianSource.reset();
    }

    @Override
    public int nextInt() {
        return (int)this.randomNumberGenerator.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        } else {
            long l = Integer.toUnsignedLong(this.nextInt());
            long m = l * (long)bound;
            long n = m & 4294967295L;
            if (n < (long)bound) {
                for (int i = Integer.remainderUnsigned(~bound + 1, bound); n < (long)i; n = m & 4294967295L) {
                    l = Integer.toUnsignedLong(this.nextInt());
                    m = l * (long)bound;
                }
            }

            long o = m >> 32;
            return (int)o;
        }
    }

    @Override
    public long nextLong() {
        return this.randomNumberGenerator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return (this.randomNumberGenerator.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return (float)this.nextBits(24) * 5.9604645E-8F;
    }

    @Override
    public double nextDouble() {
        return (double)this.nextBits(53) * 1.110223E-16F;
    }

    @Override
    public double nextGaussian() {
        return this.gaussianSource.nextGaussian();
    }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.randomNumberGenerator.nextLong();
        }
    }

    private long nextBits(int bits) {
        return this.randomNumberGenerator.nextLong() >>> 64 - bits;
    }

    public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {
        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = Mth.getSeed(x, y, z);
            long m = l ^ this.seedLo;
            return new XoroshiroRandomSource(m, this.seedHi);
        }

        @Override
        public RandomSource fromHashOf(String seed) {
            RandomSupport.Seed128bit seed128bit = RandomSupport.seedFromHashOf(seed);
            return new XoroshiroRandomSource(seed128bit.xor(this.seedLo, this.seedHi));
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new XoroshiroRandomSource(seed ^ this.seedLo, seed ^ this.seedHi);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder info) {
            info.append("seedLo: ").append(this.seedLo).append(", seedHi: ").append(this.seedHi);
        }
    }
}
