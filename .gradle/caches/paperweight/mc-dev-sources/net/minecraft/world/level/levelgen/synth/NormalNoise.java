package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;

public class NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227;
    private static final double TARGET_DEVIATION = 0.3333333333333333;
    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final NormalNoise.NoiseParameters parameters;

    @Deprecated
    public static NormalNoise createLegacyNetherBiome(RandomSource random, NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, false);
    }

    public static NormalNoise create(RandomSource random, int offset, double... octaves) {
        return create(random, new NormalNoise.NoiseParameters(offset, new DoubleArrayList(octaves)));
    }

    public static NormalNoise create(RandomSource random, NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, true);
    }

    private NormalNoise(RandomSource random, NormalNoise.NoiseParameters parameters, boolean modern) {
        int i = parameters.firstOctave;
        DoubleList doubleList = parameters.amplitudes;
        this.parameters = parameters;
        if (modern) {
            this.first = PerlinNoise.create(random, i, doubleList);
            this.second = PerlinNoise.create(random, i, doubleList);
        } else {
            this.first = PerlinNoise.createLegacyForLegacyNetherBiome(random, i, doubleList);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(random, i, doubleList);
        }

        int j = Integer.MAX_VALUE;
        int k = Integer.MIN_VALUE;
        DoubleListIterator doubleListIterator = doubleList.iterator();

        while (doubleListIterator.hasNext()) {
            int l = doubleListIterator.nextIndex();
            double d = doubleListIterator.nextDouble();
            if (d != 0.0) {
                j = Math.min(j, l);
                k = Math.max(k, l);
            }
        }

        this.valueFactor = 0.16666666666666666 / expectedDeviation(k - j);
        this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
    }

    public double maxValue() {
        return this.maxValue;
    }

    private static double expectedDeviation(int octaves) {
        return 0.1 * (1.0 + 1.0 / (double)(octaves + 1));
    }

    public double getValue(double x, double y, double z) {
        double d = x * 1.0181268882175227;
        double e = y * 1.0181268882175227;
        double f = z * 1.0181268882175227;
        return (this.first.getValue(x, y, z) + this.second.getValue(d, e, f)) * this.valueFactor;
    }

    public NormalNoise.NoiseParameters parameters() {
        return this.parameters;
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder info) {
        info.append("NormalNoise {");
        info.append("first: ");
        this.first.parityConfigString(info);
        info.append(", second: ");
        this.second.parityConfigString(info);
        info.append("}");
    }

    public static record NoiseParameters(int firstOctave, DoubleList amplitudes) {
        public static final Codec<NormalNoise.NoiseParameters> DIRECT_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        Codec.INT.fieldOf("firstOctave").forGetter(NormalNoise.NoiseParameters::firstOctave),
                        Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(NormalNoise.NoiseParameters::amplitudes)
                    )
                    .apply(instance, NormalNoise.NoiseParameters::new)
        );
        public static final Codec<Holder<NormalNoise.NoiseParameters>> CODEC = RegistryFileCodec.create(Registries.NOISE, DIRECT_CODEC);

        public NoiseParameters(int firstOctave, List<Double> amplitudes) {
            this(firstOctave, new DoubleArrayList(amplitudes));
        }

        public NoiseParameters(int firstOctave, double firstAmplitude, double... amplitudes) {
            this(firstOctave, Util.make(new DoubleArrayList(amplitudes), doubleArrayList -> doubleArrayList.add(0, firstAmplitude)));
        }
    }
}
