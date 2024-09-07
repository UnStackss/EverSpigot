package net.minecraft.world.level.levelgen.synth;

import java.util.Locale;

public class NoiseUtils {
    public static double biasTowardsExtreme(double d, double e) {
        return d + Math.sin(Math.PI * d) * e / Math.PI;
    }

    public static void parityNoiseOctaveConfigString(StringBuilder builder, double originX, double originY, double originZ, byte[] permutation) {
        builder.append(
            String.format(
                Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float)originX, (float)originY, (float)originZ, permutation[0], permutation[255]
            )
        );
    }

    public static void parityNoiseOctaveConfigString(StringBuilder builder, double originX, double originY, double originZ, int[] permutation) {
        builder.append(
            String.format(
                Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d", (float)originX, (float)originY, (float)originZ, permutation[0], permutation[255]
            )
        );
    }
}
