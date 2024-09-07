package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class SimplexNoise {
    protected static final int[][] GRADIENT = new int[][]{
        {1, 1, 0},
        {-1, 1, 0},
        {1, -1, 0},
        {-1, -1, 0},
        {1, 0, 1},
        {-1, 0, 1},
        {1, 0, -1},
        {-1, 0, -1},
        {0, 1, 1},
        {0, -1, 1},
        {0, 1, -1},
        {0, -1, -1},
        {1, 1, 0},
        {0, -1, 1},
        {-1, 1, 0},
        {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;
    private final int[] p = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        int i = 0;

        while (i < 256) {
            this.p[i] = i++;
        }

        for (int j = 0; j < 256; j++) {
            int k = random.nextInt(256 - j);
            int l = this.p[j];
            this.p[j] = this.p[k + j];
            this.p[k + j] = l;
        }
    }

    private int p(int input) {
        return this.p[input & 0xFF];
    }

    protected static double dot(int[] gradient, double x, double y, double z) {
        return (double)gradient[0] * x + (double)gradient[1] * y + (double)gradient[2] * z;
    }

    private double getCornerNoise3D(int hash, double x, double y, double z, double distance) {
        double d = distance - x * x - y * y - z * z;
        double e;
        if (d < 0.0) {
            e = 0.0;
        } else {
            d *= d;
            e = d * d * dot(GRADIENT[hash], x, y, z);
        }

        return e;
    }

    public double getValue(double x, double y) {
        double d = (x + y) * F2;
        int i = Mth.floor(x + d);
        int j = Mth.floor(y + d);
        double e = (double)(i + j) * G2;
        double f = (double)i - e;
        double g = (double)j - e;
        double h = x - f;
        double k = y - g;
        int l;
        int m;
        if (h > k) {
            l = 1;
            m = 0;
        } else {
            l = 0;
            m = 1;
        }

        double p = h - (double)l + G2;
        double q = k - (double)m + G2;
        double r = h - 1.0 + 2.0 * G2;
        double s = k - 1.0 + 2.0 * G2;
        int t = i & 0xFF;
        int u = j & 0xFF;
        int v = this.p(t + this.p(u)) % 12;
        int w = this.p(t + l + this.p(u + m)) % 12;
        int z = this.p(t + 1 + this.p(u + 1)) % 12;
        double aa = this.getCornerNoise3D(v, h, k, 0.0, 0.5);
        double ab = this.getCornerNoise3D(w, p, q, 0.0, 0.5);
        double ac = this.getCornerNoise3D(z, r, s, 0.0, 0.5);
        return 70.0 * (aa + ab + ac);
    }

    public double getValue(double x, double y, double z) {
        double d = 0.3333333333333333;
        double e = (x + y + z) * 0.3333333333333333;
        int i = Mth.floor(x + e);
        int j = Mth.floor(y + e);
        int k = Mth.floor(z + e);
        double f = 0.16666666666666666;
        double g = (double)(i + j + k) * 0.16666666666666666;
        double h = (double)i - g;
        double l = (double)j - g;
        double m = (double)k - g;
        double n = x - h;
        double o = y - l;
        double p = z - m;
        int q;
        int r;
        int s;
        int t;
        int u;
        int v;
        if (n >= o) {
            if (o >= p) {
                q = 1;
                r = 0;
                s = 0;
                t = 1;
                u = 1;
                v = 0;
            } else if (n >= p) {
                q = 1;
                r = 0;
                s = 0;
                t = 1;
                u = 0;
                v = 1;
            } else {
                q = 0;
                r = 0;
                s = 1;
                t = 1;
                u = 0;
                v = 1;
            }
        } else if (o < p) {
            q = 0;
            r = 0;
            s = 1;
            t = 0;
            u = 1;
            v = 1;
        } else if (n < p) {
            q = 0;
            r = 1;
            s = 0;
            t = 0;
            u = 1;
            v = 1;
        } else {
            q = 0;
            r = 1;
            s = 0;
            t = 1;
            u = 1;
            v = 0;
        }

        double bd = n - (double)q + 0.16666666666666666;
        double be = o - (double)r + 0.16666666666666666;
        double bf = p - (double)s + 0.16666666666666666;
        double bg = n - (double)t + 0.3333333333333333;
        double bh = o - (double)u + 0.3333333333333333;
        double bi = p - (double)v + 0.3333333333333333;
        double bj = n - 1.0 + 0.5;
        double bk = o - 1.0 + 0.5;
        double bl = p - 1.0 + 0.5;
        int bm = i & 0xFF;
        int bn = j & 0xFF;
        int bo = k & 0xFF;
        int bp = this.p(bm + this.p(bn + this.p(bo))) % 12;
        int bq = this.p(bm + q + this.p(bn + r + this.p(bo + s))) % 12;
        int br = this.p(bm + t + this.p(bn + u + this.p(bo + v))) % 12;
        int bs = this.p(bm + 1 + this.p(bn + 1 + this.p(bo + 1))) % 12;
        double bt = this.getCornerNoise3D(bp, n, o, p, 0.6);
        double bu = this.getCornerNoise3D(bq, bd, be, bf, 0.6);
        double bv = this.getCornerNoise3D(br, bg, bh, bi, 0.6);
        double bw = this.getCornerNoise3D(bs, bj, bk, bl, 0.6);
        return 32.0 * (bt + bu + bv + bw);
    }
}
