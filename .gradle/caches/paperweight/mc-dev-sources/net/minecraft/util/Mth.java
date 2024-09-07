package net.minecraft.util;

import java.util.Locale;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.math.NumberUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Mth {
    private static final long UUID_VERSION = 61440L;
    private static final long UUID_VERSION_TYPE_4 = 16384L;
    private static final long UUID_VARIANT = -4611686018427387904L;
    private static final long UUID_VARIANT_2 = Long.MIN_VALUE;
    public static final float PI = (float) Math.PI;
    public static final float HALF_PI = (float) (Math.PI / 2);
    public static final float TWO_PI = (float) (Math.PI * 2);
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    public static final float RAD_TO_DEG = 180.0F / (float)Math.PI;
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = sqrt(2.0F);
    private static final float SIN_SCALE = 10430.378F;
    public static final Vector3f Y_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
    public static final Vector3f X_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);
    public static final Vector3f Z_AXIS = new Vector3f(0.0F, 0.0F, 1.0F);
    private static final float[] SIN = Util.make(new float[65536], sineTable -> {
        for (int ix = 0; ix < sineTable.length; ix++) {
            sineTable[ix] = (float)Math.sin((double)ix * Math.PI * 2.0 / 65536.0);
        }
    });
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = new int[]{
        0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
    };
    private static final double ONE_SIXTH = 0.16666666666666666;
    private static final int FRAC_EXP = 8;
    private static final int LUT_SIZE = 257;
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    public static float sin(float value) {
        return SIN[(int)(value * 10430.378F) & 65535];
    }

    public static float cos(float value) {
        return SIN[(int)(value * 10430.378F + 16384.0F) & 65535];
    }

    public static float sqrt(float value) {
        return (float)Math.sqrt((double)value);
    }

    public static int floor(float value) {
        int i = (int)value;
        return value < (float)i ? i - 1 : i;
    }

    public static int floor(double value) {
        int i = (int)value;
        return value < (double)i ? i - 1 : i;
    }

    public static long lfloor(double value) {
        long l = (long)value;
        return value < (double)l ? l - 1L : l;
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static int ceil(float value) {
        int i = (int)value;
        return value > (float)i ? i + 1 : i;
    }

    public static int ceil(double value) {
        int i = (int)value;
        return value > (double)i ? i + 1 : i;
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clampedLerp(double start, double end, double delta) {
        if (delta < 0.0) {
            return start;
        } else {
            return delta > 1.0 ? end : lerp(delta, start, end);
        }
    }

    public static float clampedLerp(float start, float end, float delta) {
        if (delta < 0.0F) {
            return start;
        } else {
            return delta > 1.0F ? end : lerp(delta, start, end);
        }
    }

    public static double absMax(double a, double b) {
        if (a < 0.0) {
            a = -a;
        }

        if (b < 0.0) {
            b = -b;
        }

        return Math.max(a, b);
    }

    public static int floorDiv(int dividend, int divisor) {
        return Math.floorDiv(dividend, divisor);
    }

    public static int nextInt(RandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    public static float nextFloat(RandomSource random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(RandomSource random, double min, double max) {
        return min >= max ? min : random.nextDouble() * (max - min) + min;
    }

    public static boolean equal(float a, float b) {
        return Math.abs(b - a) < 1.0E-5F;
    }

    public static boolean equal(double a, double b) {
        return Math.abs(b - a) < 1.0E-5F;
    }

    public static int positiveModulo(int dividend, int divisor) {
        return Math.floorMod(dividend, divisor);
    }

    public static float positiveModulo(float dividend, float divisor) {
        return (dividend % divisor + divisor) % divisor;
    }

    public static double positiveModulo(double dividend, double divisor) {
        return (dividend % divisor + divisor) % divisor;
    }

    public static boolean isMultipleOf(int a, int b) {
        return a % b == 0;
    }

    public static int wrapDegrees(int degrees) {
        int i = degrees % 360;
        if (i >= 180) {
            i -= 360;
        }

        if (i < -180) {
            i += 360;
        }

        return i;
    }

    public static float wrapDegrees(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) {
            f -= 360.0F;
        }

        if (f < -180.0F) {
            f += 360.0F;
        }

        return f;
    }

    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }

        if (d < -180.0) {
            d += 360.0;
        }

        return d;
    }

    public static float degreesDifference(float start, float end) {
        return wrapDegrees(end - start);
    }

    public static float degreesDifferenceAbs(float first, float second) {
        return abs(degreesDifference(first, second));
    }

    public static float rotateIfNecessary(float value, float mean, float delta) {
        float f = degreesDifference(value, mean);
        float g = clamp(f, -delta, delta);
        return mean - g;
    }

    public static float approach(float from, float to, float step) {
        step = abs(step);
        return from < to ? clamp(from + step, from, to) : clamp(from - step, to, from);
    }

    public static float approachDegrees(float from, float to, float step) {
        float f = degreesDifference(from, to);
        return approach(from, from + f, step);
    }

    public static int getInt(String string, int fallback) {
        return NumberUtils.toInt(string, fallback);
    }

    public static int smallestEncompassingPowerOfTwo(int value) {
        int i = value - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }

    public static boolean isPowerOfTwo(int value) {
        return value != 0 && (value & value - 1) == 0;
    }

    public static int ceillog2(int value) {
        value = isPowerOfTwo(value) ? value : smallestEncompassingPowerOfTwo(value);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int)((long)value * 125613361L >> 27) & 31];
    }

    public static int log2(int value) {
        return ceillog2(value) - (isPowerOfTwo(value) ? 0 : 1);
    }

    public static int color(float r, float g, float b) {
        return FastColor.ARGB32.color(0, floor(r * 255.0F), floor(g * 255.0F), floor(b * 255.0F));
    }

    public static float frac(float value) {
        return value - (float)floor(value);
    }

    public static double frac(double value) {
        return value - (double)lfloor(value);
    }

    @Deprecated
    public static long getSeed(Vec3i vec) {
        return getSeed(vec.getX(), vec.getY(), vec.getZ());
    }

    @Deprecated
    public static long getSeed(int x, int y, int z) {
        long l = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    public static UUID createInsecureUUID(RandomSource random) {
        long l = random.nextLong() & -61441L | 16384L;
        long m = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new UUID(l, m);
    }

    public static UUID createInsecureUUID() {
        return createInsecureUUID(RANDOM);
    }

    public static double inverseLerp(double value, double start, double end) {
        return (value - start) / (end - start);
    }

    public static float inverseLerp(float value, float start, float end) {
        return (value - start) / (end - start);
    }

    public static boolean rayIntersectsAABB(Vec3 origin, Vec3 direction, AABB box) {
        double d = (box.minX + box.maxX) * 0.5;
        double e = (box.maxX - box.minX) * 0.5;
        double f = origin.x - d;
        if (Math.abs(f) > e && f * direction.x >= 0.0) {
            return false;
        } else {
            double g = (box.minY + box.maxY) * 0.5;
            double h = (box.maxY - box.minY) * 0.5;
            double i = origin.y - g;
            if (Math.abs(i) > h && i * direction.y >= 0.0) {
                return false;
            } else {
                double j = (box.minZ + box.maxZ) * 0.5;
                double k = (box.maxZ - box.minZ) * 0.5;
                double l = origin.z - j;
                if (Math.abs(l) > k && l * direction.z >= 0.0) {
                    return false;
                } else {
                    double m = Math.abs(direction.x);
                    double n = Math.abs(direction.y);
                    double o = Math.abs(direction.z);
                    double p = direction.y * l - direction.z * i;
                    if (Math.abs(p) > h * o + k * n) {
                        return false;
                    } else {
                        p = direction.z * f - direction.x * l;
                        if (Math.abs(p) > e * o + k * m) {
                            return false;
                        } else {
                            p = direction.x * i - direction.y * f;
                            return Math.abs(p) < e * n + h * m;
                        }
                    }
                }
            }
        }
    }

    public static double atan2(double y, double x) {
        double d = x * x + y * y;
        if (Double.isNaN(d)) {
            return Double.NaN;
        } else {
            boolean bl = y < 0.0;
            if (bl) {
                y = -y;
            }

            boolean bl2 = x < 0.0;
            if (bl2) {
                x = -x;
            }

            boolean bl3 = y > x;
            if (bl3) {
                double e = x;
                x = y;
                y = e;
            }

            double f = fastInvSqrt(d);
            x *= f;
            y *= f;
            double g = FRAC_BIAS + y;
            int i = (int)Double.doubleToRawLongBits(g);
            double h = ASIN_TAB[i];
            double j = COS_TAB[i];
            double k = g - FRAC_BIAS;
            double l = y * j - x * k;
            double m = (6.0 + l * l) * l * 0.16666666666666666;
            double n = h + m;
            if (bl3) {
                n = (Math.PI / 2) - n;
            }

            if (bl2) {
                n = Math.PI - n;
            }

            if (bl) {
                n = -n;
            }

            return n;
        }
    }

    public static float invSqrt(float x) {
        return org.joml.Math.invsqrt(x);
    }

    public static double invSqrt(double x) {
        return org.joml.Math.invsqrt(x);
    }

    @Deprecated
    public static double fastInvSqrt(double x) {
        double d = 0.5 * x;
        long l = Double.doubleToRawLongBits(x);
        l = 6910469410427058090L - (l >> 1);
        x = Double.longBitsToDouble(l);
        return x * (1.5 - d * x * x);
    }

    public static float fastInvCubeRoot(float x) {
        int i = Float.floatToIntBits(x);
        i = 1419967116 - i / 3;
        float f = Float.intBitsToFloat(i);
        f = 0.6666667F * f + 1.0F / (3.0F * f * f * x);
        return 0.6666667F * f + 1.0F / (3.0F * f * f * x);
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        return hsvToArgb(hue, saturation, value, 0);
    }

    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        int i = (int)(hue * 6.0F) % 6;
        float f = hue * 6.0F - (float)i;
        float g = value * (1.0F - saturation);
        float h = value * (1.0F - f * saturation);
        float j = value * (1.0F - (1.0F - f) * saturation);
        float k;
        float l;
        float m;
        switch (i) {
            case 0:
                k = value;
                l = j;
                m = g;
                break;
            case 1:
                k = h;
                l = value;
                m = g;
                break;
            case 2:
                k = g;
                l = value;
                m = j;
                break;
            case 3:
                k = g;
                l = h;
                m = value;
                break;
            case 4:
                k = j;
                l = g;
                m = value;
                break;
            case 5:
                k = value;
                l = g;
                m = h;
                break;
            default:
                throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }

        return FastColor.ARGB32.color(alpha, clamp((int)(k * 255.0F), 0, 255), clamp((int)(l * 255.0F), 0, 255), clamp((int)(m * 255.0F), 0, 255));
    }

    public static int murmurHash3Mixer(int value) {
        value ^= value >>> 16;
        value *= -2048144789;
        value ^= value >>> 13;
        value *= -1028477387;
        return value ^ value >>> 16;
    }

    public static int binarySearch(int min, int max, IntPredicate predicate) {
        int i = max - min;

        while (i > 0) {
            int j = i / 2;
            int k = min + j;
            if (predicate.test(k)) {
                i = j;
            } else {
                min = k + 1;
                i -= j + 1;
            }
        }

        return min;
    }

    public static int lerpInt(float delta, int start, int end) {
        return start + floor(delta * (float)(end - start));
    }

    public static int lerpDiscrete(float delta, int start, int end) {
        int i = end - start;
        return start + floor(delta * (float)(i - 1)) + (delta > 0.0F ? 1 : 0);
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static double lerp2(double deltaX, double deltaY, double x0y0, double x1y0, double x0y1, double x1y1) {
        return lerp(deltaY, lerp(deltaX, x0y0, x1y0), lerp(deltaX, x0y1, x1y1));
    }

    public static double lerp3(
        double deltaX,
        double deltaY,
        double deltaZ,
        double x0y0z0,
        double x1y0z0,
        double x0y1z0,
        double x1y1z0,
        double x0y0z1,
        double x1y0z1,
        double x0y1z1,
        double x1y1z1
    ) {
        return lerp(deltaZ, lerp2(deltaX, deltaY, x0y0z0, x1y0z0, x0y1z0, x1y1z0), lerp2(deltaX, deltaY, x0y0z1, x1y0z1, x0y1z1, x1y1z1));
    }

    public static float catmullrom(float delta, float p0, float p1, float p2, float p3) {
        return 0.5F
            * (
                2.0F * p1
                    + (p2 - p0) * delta
                    + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * delta * delta
                    + (3.0F * p1 - p0 - 3.0F * p2 + p3) * delta * delta * delta
            );
    }

    public static double smoothstep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    public static double smoothstepDerivative(double value) {
        return 30.0 * value * value * (value - 1.0) * (value - 1.0);
    }

    public static int sign(double value) {
        if (value == 0.0) {
            return 0;
        } else {
            return value > 0.0 ? 1 : -1;
        }
    }

    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static double rotLerp(double delta, double start, double end) {
        return start + delta * wrapDegrees(end - start);
    }

    public static float triangleWave(float value, float maxDeviation) {
        return (Math.abs(value % maxDeviation - maxDeviation * 0.5F) - maxDeviation * 0.25F) / (maxDeviation * 0.25F);
    }

    public static float square(float n) {
        return n * n;
    }

    public static double square(double n) {
        return n * n;
    }

    public static int square(int n) {
        return n * n;
    }

    public static long square(long n) {
        return n * n;
    }

    public static double clampedMap(double value, double oldStart, double oldEnd, double newStart, double newEnd) {
        return clampedLerp(newStart, newEnd, inverseLerp(value, oldStart, oldEnd));
    }

    public static float clampedMap(float value, float oldStart, float oldEnd, float newStart, float newEnd) {
        return clampedLerp(newStart, newEnd, inverseLerp(value, oldStart, oldEnd));
    }

    public static double map(double value, double oldStart, double oldEnd, double newStart, double newEnd) {
        return lerp(inverseLerp(value, oldStart, oldEnd), newStart, newEnd);
    }

    public static float map(float value, float oldStart, float oldEnd, float newStart, float newEnd) {
        return lerp(inverseLerp(value, oldStart, oldEnd), newStart, newEnd);
    }

    public static double wobble(double d) {
        return d + (2.0 * RandomSource.create((long)floor(d * 3000.0)).nextDouble() - 1.0) * 1.0E-7 / 2.0;
    }

    public static int roundToward(int value, int divisor) {
        return positiveCeilDiv(value, divisor) * divisor;
    }

    public static int positiveCeilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }

    public static int randomBetweenInclusive(RandomSource random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public static float randomBetween(RandomSource random, float min, float max) {
        return random.nextFloat() * (max - min) + min;
    }

    public static float normal(RandomSource random, float mean, float deviation) {
        return mean + (float)random.nextGaussian() * deviation;
    }

    public static double lengthSquared(double a, double b) {
        return a * a + b * b;
    }

    public static double length(double a, double b) {
        return Math.sqrt(lengthSquared(a, b));
    }

    public static double lengthSquared(double a, double b, double c) {
        return a * a + b * b + c * c;
    }

    public static double length(double a, double b, double c) {
        return Math.sqrt(lengthSquared(a, b, c));
    }

    public static float lengthSquared(float a, float b, float c) {
        return a * a + b * b + c * c;
    }

    public static int quantize(double a, int b) {
        return floor(a / (double)b) * b;
    }

    public static IntStream outFromOrigin(int seed, int lowerBound, int upperBound) {
        return outFromOrigin(seed, lowerBound, upperBound, 1);
    }

    public static IntStream outFromOrigin(int seed, int lowerBound, int upperBound, int steps) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "upperbound %d expected to be > lowerBound %d", upperBound, lowerBound));
        } else if (steps < 1) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "steps expected to be >= 1, was %d", steps));
        } else {
            return seed >= lowerBound && seed <= upperBound ? IntStream.iterate(seed, i -> {
                int m = Math.abs(seed - i);
                return seed - m >= lowerBound || seed + m <= upperBound;
            }, i -> {
                boolean bl = i <= seed;
                int n = Math.abs(seed - i);
                boolean bl2 = seed + n + steps <= upperBound;
                if (!bl || !bl2) {
                    int o = seed - n - (bl ? steps : 0);
                    if (o >= lowerBound) {
                        return o;
                    }
                }

                return seed + n + steps;
            }) : IntStream.empty();
        }
    }

    public static Quaternionf rotationAroundAxis(Vector3f axis, Quaternionf rotation, Quaternionf result) {
        float f = axis.dot(rotation.x, rotation.y, rotation.z);
        return result.set(axis.x * f, axis.y * f, axis.z * f, rotation.w).normalize();
    }

    public static int mulAndTruncate(Fraction fraction, int multiplier) {
        return fraction.getNumerator() * multiplier / fraction.getDenominator();
    }

    static {
        for (int i = 0; i < 257; i++) {
            double d = (double)i / 256.0;
            double e = Math.asin(d);
            COS_TAB[i] = Math.cos(e);
            ASIN_TAB[i] = e;
        }
    }
}
