package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public class PerlinNoise {
    private static final int ROUND_OFF = 33554432;
    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final DoubleList amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    @Deprecated
    public static PerlinNoise createLegacyForBlendedNoise(RandomSource random, IntStream octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves.boxed().collect(ImmutableList.toImmutableList()))), false);
    }

    @Deprecated
    public static PerlinNoise createLegacyForLegacyNetherBiome(RandomSource random, int offset, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(offset, amplitudes), false);
    }

    public static PerlinNoise create(RandomSource random, IntStream octaves) {
        return create(random, octaves.boxed().collect(ImmutableList.toImmutableList()));
    }

    public static PerlinNoise create(RandomSource random, List<Integer> octaves) {
        return new PerlinNoise(random, makeAmplitudes(new IntRBTreeSet(octaves)), true);
    }

    public static PerlinNoise create(RandomSource random, int offset, double firstAmplitude, double... amplitudes) {
        DoubleArrayList doubleArrayList = new DoubleArrayList(amplitudes);
        doubleArrayList.add(0, firstAmplitude);
        return new PerlinNoise(random, Pair.of(offset, doubleArrayList), true);
    }

    public static PerlinNoise create(RandomSource random, int offset, DoubleList amplitudes) {
        return new PerlinNoise(random, Pair.of(offset, amplitudes), true);
    }

    private static Pair<Integer, DoubleList> makeAmplitudes(IntSortedSet octaves) {
        if (octaves.isEmpty()) {
            throw new IllegalArgumentException("Need some octaves!");
        } else {
            int i = -octaves.firstInt();
            int j = octaves.lastInt();
            int k = i + j + 1;
            if (k < 1) {
                throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
            } else {
                DoubleList doubleList = new DoubleArrayList(new double[k]);
                IntBidirectionalIterator intBidirectionalIterator = octaves.iterator();

                while (intBidirectionalIterator.hasNext()) {
                    int l = intBidirectionalIterator.nextInt();
                    doubleList.set(l + i, 1.0);
                }

                return Pair.of(-i, doubleList);
            }
        }
    }

    protected PerlinNoise(RandomSource random, Pair<Integer, DoubleList> firstOctaveAndAmplitudes, boolean xoroshiro) {
        this.firstOctave = firstOctaveAndAmplitudes.getFirst();
        this.amplitudes = firstOctaveAndAmplitudes.getSecond();
        int i = this.amplitudes.size();
        int j = -this.firstOctave;
        this.noiseLevels = new ImprovedNoise[i];
        if (xoroshiro) {
            PositionalRandomFactory positionalRandomFactory = random.forkPositional();

            for (int k = 0; k < i; k++) {
                if (this.amplitudes.getDouble(k) != 0.0) {
                    int l = this.firstOctave + k;
                    this.noiseLevels[k] = new ImprovedNoise(positionalRandomFactory.fromHashOf("octave_" + l));
                }
            }
        } else {
            ImprovedNoise improvedNoise = new ImprovedNoise(random);
            if (j >= 0 && j < i) {
                double d = this.amplitudes.getDouble(j);
                if (d != 0.0) {
                    this.noiseLevels[j] = improvedNoise;
                }
            }

            for (int m = j - 1; m >= 0; m--) {
                if (m < i) {
                    double e = this.amplitudes.getDouble(m);
                    if (e != 0.0) {
                        this.noiseLevels[m] = new ImprovedNoise(random);
                    } else {
                        skipOctave(random);
                    }
                } else {
                    skipOctave(random);
                }
            }

            if (Arrays.stream(this.noiseLevels).filter(Objects::nonNull).count() != this.amplitudes.stream().filter(amplitude -> amplitude != 0.0).count()) {
                throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
            }

            if (j < i - 1) {
                throw new IllegalArgumentException("Positive octaves are temporarily disabled");
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, (double)(-j));
        this.lowestFreqValueFactor = Math.pow(2.0, (double)(i - 1)) / (Math.pow(2.0, (double)i) - 1.0);
        this.maxValue = this.edgeValue(2.0);
    }

    protected double maxValue() {
        return this.maxValue;
    }

    private static void skipOctave(RandomSource random) {
        random.consumeCount(262);
    }

    public double getValue(double x, double y, double z) {
        return this.getValue(x, y, z, 0.0, 0.0, false);
    }

    @Deprecated
    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useOrigin) {
        double d = 0.0;
        double e = this.lowestFreqInputFactor;
        double f = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise != null) {
                double g = improvedNoise.noise(wrap(x * e), useOrigin ? -improvedNoise.yo : wrap(y * e), wrap(z * e), yScale * e, yMax * e);
                d += this.amplitudes.getDouble(i) * g * f;
            }

            e *= 2.0;
            f /= 2.0;
        }

        return d;
    }

    public double maxBrokenValue(double d) {
        return this.edgeValue(d + 2.0);
    }

    private double edgeValue(double scale) {
        double d = 0.0;
        double e = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise != null) {
                d += this.amplitudes.getDouble(i) * scale * e;
            }

            e /= 2.0;
        }

        return d;
    }

    @Nullable
    public ImprovedNoise getOctaveNoise(int octave) {
        return this.noiseLevels[this.noiseLevels.length - 1 - octave];
    }

    public static double wrap(double value) {
        return value - (double)Mth.lfloor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
    }

    protected int firstOctave() {
        return this.firstOctave;
    }

    protected DoubleList amplitudes() {
        return this.amplitudes;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder info) {
        info.append("PerlinNoise{");
        List<String> list = this.amplitudes.stream().map(double_ -> String.format(Locale.ROOT, "%.2f", double_)).toList();
        info.append("first octave: ").append(this.firstOctave).append(", amplitudes: ").append(list).append(", noise levels: [");

        for (int i = 0; i < this.noiseLevels.length; i++) {
            info.append(i).append(": ");
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise == null) {
                info.append("null");
            } else {
                improvedNoise.parityConfigString(info);
            }

            info.append(", ");
        }

        info.append("]");
        info.append("}");
    }
}
