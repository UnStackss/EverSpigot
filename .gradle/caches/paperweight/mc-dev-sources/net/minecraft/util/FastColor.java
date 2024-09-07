package net.minecraft.util;

public class FastColor {
    public static int as8BitChannel(float value) {
        return Mth.floor(value * 255.0F);
    }

    public static class ABGR32 {
        public static int alpha(int abgr) {
            return abgr >>> 24;
        }

        public static int red(int abgr) {
            return abgr & 0xFF;
        }

        public static int green(int abgr) {
            return abgr >> 8 & 0xFF;
        }

        public static int blue(int abgr) {
            return abgr >> 16 & 0xFF;
        }

        public static int transparent(int abgr) {
            return abgr & 16777215;
        }

        public static int opaque(int abgr) {
            return abgr | 0xFF000000;
        }

        public static int color(int a, int b, int g, int r) {
            return a << 24 | b << 16 | g << 8 | r;
        }

        public static int color(int alpha, int bgr) {
            return alpha << 24 | bgr & 16777215;
        }

        public static int fromArgb32(int argb) {
            return argb & -16711936 | (argb & 0xFF0000) >> 16 | (argb & 0xFF) << 16;
        }
    }

    public static class ARGB32 {
        public static int alpha(int argb) {
            return argb >>> 24;
        }

        public static int red(int argb) {
            return argb >> 16 & 0xFF;
        }

        public static int green(int argb) {
            return argb >> 8 & 0xFF;
        }

        public static int blue(int argb) {
            return argb & 0xFF;
        }

        public static int color(int alpha, int red, int green, int blue) {
            return alpha << 24 | red << 16 | green << 8 | blue;
        }

        public static int color(int red, int green, int blue) {
            return color(255, red, green, blue);
        }

        public static int multiply(int first, int second) {
            return color(
                alpha(first) * alpha(second) / 255, red(first) * red(second) / 255, green(first) * green(second) / 255, blue(first) * blue(second) / 255
            );
        }

        public static int lerp(float delta, int start, int end) {
            int i = Mth.lerpInt(delta, alpha(start), alpha(end));
            int j = Mth.lerpInt(delta, red(start), red(end));
            int k = Mth.lerpInt(delta, green(start), green(end));
            int l = Mth.lerpInt(delta, blue(start), blue(end));
            return color(i, j, k, l);
        }

        public static int opaque(int argb) {
            return argb | 0xFF000000;
        }

        public static int color(int alpha, int rgb) {
            return alpha << 24 | rgb & 16777215;
        }

        public static int colorFromFloat(float a, float r, float g, float b) {
            return color(FastColor.as8BitChannel(a), FastColor.as8BitChannel(r), FastColor.as8BitChannel(g), FastColor.as8BitChannel(b));
        }

        public static int average(int a, int b) {
            return color((alpha(a) + alpha(b)) / 2, (red(a) + red(b)) / 2, (green(a) + green(b)) / 2, (blue(a) + blue(b)) / 2);
        }
    }
}
