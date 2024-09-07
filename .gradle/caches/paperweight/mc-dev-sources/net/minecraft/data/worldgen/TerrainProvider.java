package net.minecraft.data.worldgen;

import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public class TerrainProvider {
    private static final float DEEP_OCEAN_CONTINENTALNESS = -0.51F;
    private static final float OCEAN_CONTINENTALNESS = -0.4F;
    private static final float PLAINS_CONTINENTALNESS = 0.1F;
    private static final float BEACH_CONTINENTALNESS = -0.15F;
    private static final ToFloatFunction<Float> NO_TRANSFORM = ToFloatFunction.IDENTITY;
    private static final ToFloatFunction<Float> AMPLIFIED_OFFSET = ToFloatFunction.createUnlimited(value -> value < 0.0F ? value : value * 2.0F);
    private static final ToFloatFunction<Float> AMPLIFIED_FACTOR = ToFloatFunction.createUnlimited(value -> 1.25F - 6.25F / (value + 5.0F));
    private static final ToFloatFunction<Float> AMPLIFIED_JAGGEDNESS = ToFloatFunction.createUnlimited(value -> value * 2.0F);

    public static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> overworldOffset(I continents, I erosion, I ridgesFolded, boolean amplified) {
        ToFloatFunction<Float> toFloatFunction = amplified ? AMPLIFIED_OFFSET : NO_TRANSFORM;
        CubicSpline<C, I> cubicSpline = buildErosionOffsetSpline(erosion, ridgesFolded, -0.15F, 0.0F, 0.0F, 0.1F, 0.0F, -0.03F, false, false, toFloatFunction);
        CubicSpline<C, I> cubicSpline2 = buildErosionOffsetSpline(erosion, ridgesFolded, -0.1F, 0.03F, 0.1F, 0.1F, 0.01F, -0.03F, false, false, toFloatFunction);
        CubicSpline<C, I> cubicSpline3 = buildErosionOffsetSpline(erosion, ridgesFolded, -0.1F, 0.03F, 0.1F, 0.7F, 0.01F, -0.03F, true, true, toFloatFunction);
        CubicSpline<C, I> cubicSpline4 = buildErosionOffsetSpline(erosion, ridgesFolded, -0.05F, 0.03F, 0.1F, 1.0F, 0.01F, 0.01F, true, true, toFloatFunction);
        return CubicSpline.<C, I>builder(continents, toFloatFunction)
            .addPoint(-1.1F, 0.044F)
            .addPoint(-1.02F, -0.2222F)
            .addPoint(-0.51F, -0.2222F)
            .addPoint(-0.44F, -0.12F)
            .addPoint(-0.18F, -0.12F)
            .addPoint(-0.16F, cubicSpline)
            .addPoint(-0.15F, cubicSpline)
            .addPoint(-0.1F, cubicSpline2)
            .addPoint(0.25F, cubicSpline3)
            .addPoint(1.0F, cubicSpline4)
            .build();
    }

    public static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> overworldFactor(I continents, I erosion, I ridges, I ridgesFolded, boolean amplified) {
        ToFloatFunction<Float> toFloatFunction = amplified ? AMPLIFIED_FACTOR : NO_TRANSFORM;
        return CubicSpline.<C, I>builder(continents, NO_TRANSFORM)
            .addPoint(-0.19F, 3.95F)
            .addPoint(-0.15F, getErosionFactor(erosion, ridges, ridgesFolded, 6.25F, true, NO_TRANSFORM))
            .addPoint(-0.1F, getErosionFactor(erosion, ridges, ridgesFolded, 5.47F, true, toFloatFunction))
            .addPoint(0.03F, getErosionFactor(erosion, ridges, ridgesFolded, 5.08F, true, toFloatFunction))
            .addPoint(0.06F, getErosionFactor(erosion, ridges, ridgesFolded, 4.69F, false, toFloatFunction))
            .build();
    }

    public static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> overworldJaggedness(I continents, I erosion, I ridges, I ridgesFolded, boolean amplified) {
        ToFloatFunction<Float> toFloatFunction = amplified ? AMPLIFIED_JAGGEDNESS : NO_TRANSFORM;
        float f = 0.65F;
        return CubicSpline.<C, I>builder(continents, toFloatFunction)
            .addPoint(-0.11F, 0.0F)
            .addPoint(0.03F, buildErosionJaggednessSpline(erosion, ridges, ridgesFolded, 1.0F, 0.5F, 0.0F, 0.0F, toFloatFunction))
            .addPoint(0.65F, buildErosionJaggednessSpline(erosion, ridges, ridgesFolded, 1.0F, 1.0F, 1.0F, 0.0F, toFloatFunction))
            .build();
    }

    private static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> buildErosionJaggednessSpline(
        I erosion, I ridges, I ridgesFolded, float f, float g, float h, float i, ToFloatFunction<Float> amplifier
    ) {
        float j = -0.5775F;
        CubicSpline<C, I> cubicSpline = buildRidgeJaggednessSpline(ridges, ridgesFolded, f, h, amplifier);
        CubicSpline<C, I> cubicSpline2 = buildRidgeJaggednessSpline(ridges, ridgesFolded, g, i, amplifier);
        return CubicSpline.<C, I>builder(erosion, amplifier)
            .addPoint(-1.0F, cubicSpline)
            .addPoint(-0.78F, cubicSpline2)
            .addPoint(-0.5775F, cubicSpline2)
            .addPoint(-0.375F, 0.0F)
            .build();
    }

    private static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> buildRidgeJaggednessSpline(
        I ridges, I ridgesFolded, float f, float g, ToFloatFunction<Float> amplifier
    ) {
        float h = NoiseRouterData.peaksAndValleys(0.4F);
        float i = NoiseRouterData.peaksAndValleys(0.56666666F);
        float j = (h + i) / 2.0F;
        CubicSpline.Builder<C, I> builder = CubicSpline.builder(ridgesFolded, amplifier);
        builder.addPoint(h, 0.0F);
        if (g > 0.0F) {
            builder.addPoint(j, buildWeirdnessJaggednessSpline(ridges, g, amplifier));
        } else {
            builder.addPoint(j, 0.0F);
        }

        if (f > 0.0F) {
            builder.addPoint(1.0F, buildWeirdnessJaggednessSpline(ridges, f, amplifier));
        } else {
            builder.addPoint(1.0F, 0.0F);
        }

        return builder.build();
    }

    private static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> buildWeirdnessJaggednessSpline(I ridges, float f, ToFloatFunction<Float> amplifier) {
        float g = 0.63F * f;
        float h = 0.3F * f;
        return CubicSpline.<C, I>builder(ridges, amplifier).addPoint(-0.01F, g).addPoint(0.01F, h).build();
    }

    private static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> getErosionFactor(
        I erosion, I ridges, I ridgesFolded, float f, boolean bl, ToFloatFunction<Float> amplifier
    ) {
        CubicSpline<C, I> cubicSpline = CubicSpline.<C, I>builder(ridges, amplifier).addPoint(-0.2F, 6.3F).addPoint(0.2F, f).build();
        CubicSpline.Builder<C, I> builder = CubicSpline.<C, I>builder(erosion, amplifier)
            .addPoint(-0.6F, cubicSpline)
            .addPoint(-0.5F, CubicSpline.<C, I>builder(ridges, amplifier).addPoint(-0.05F, 6.3F).addPoint(0.05F, 2.67F).build())
            .addPoint(-0.35F, cubicSpline)
            .addPoint(-0.25F, cubicSpline)
            .addPoint(-0.1F, CubicSpline.<C, I>builder(ridges, amplifier).addPoint(-0.05F, 2.67F).addPoint(0.05F, 6.3F).build())
            .addPoint(0.03F, cubicSpline);
        if (bl) {
            CubicSpline<C, I> cubicSpline2 = CubicSpline.<C, I>builder(ridges, amplifier).addPoint(0.0F, f).addPoint(0.1F, 0.625F).build();
            CubicSpline<C, I> cubicSpline3 = CubicSpline.<C, I>builder(ridgesFolded, amplifier).addPoint(-0.9F, f).addPoint(-0.69F, cubicSpline2).build();
            builder.addPoint(0.35F, f).addPoint(0.45F, cubicSpline3).addPoint(0.55F, cubicSpline3).addPoint(0.62F, f);
        } else {
            CubicSpline<C, I> cubicSpline4 = CubicSpline.<C, I>builder(ridgesFolded, amplifier).addPoint(-0.7F, cubicSpline).addPoint(-0.15F, 1.37F).build();
            CubicSpline<C, I> cubicSpline5 = CubicSpline.<C, I>builder(ridgesFolded, amplifier).addPoint(0.45F, cubicSpline).addPoint(0.7F, 1.56F).build();
            builder.addPoint(0.05F, cubicSpline5).addPoint(0.4F, cubicSpline5).addPoint(0.45F, cubicSpline4).addPoint(0.55F, cubicSpline4).addPoint(0.58F, f);
        }

        return builder.build();
    }

    private static float calculateSlope(float f, float g, float h, float i) {
        return (g - f) / (i - h);
    }

    private static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> buildMountainRidgeSplineWithPoints(
        I ridgesFolded, float f, boolean bl, ToFloatFunction<Float> amplifier
    ) {
        CubicSpline.Builder<C, I> builder = CubicSpline.builder(ridgesFolded, amplifier);
        float g = -0.7F;
        float h = -1.0F;
        float i = mountainContinentalness(-1.0F, f, -0.7F);
        float j = 1.0F;
        float k = mountainContinentalness(1.0F, f, -0.7F);
        float l = calculateMountainRidgeZeroContinentalnessPoint(f);
        float m = -0.65F;
        if (-0.65F < l && l < 1.0F) {
            float n = mountainContinentalness(-0.65F, f, -0.7F);
            float o = -0.75F;
            float p = mountainContinentalness(-0.75F, f, -0.7F);
            float q = calculateSlope(i, p, -1.0F, -0.75F);
            builder.addPoint(-1.0F, i, q);
            builder.addPoint(-0.75F, p);
            builder.addPoint(-0.65F, n);
            float r = mountainContinentalness(l, f, -0.7F);
            float s = calculateSlope(r, k, l, 1.0F);
            float t = 0.01F;
            builder.addPoint(l - 0.01F, r);
            builder.addPoint(l, r, s);
            builder.addPoint(1.0F, k, s);
        } else {
            float u = calculateSlope(i, k, -1.0F, 1.0F);
            if (bl) {
                builder.addPoint(-1.0F, Math.max(0.2F, i));
                builder.addPoint(0.0F, Mth.lerp(0.5F, i, k), u);
            } else {
                builder.addPoint(-1.0F, i, u);
            }

            builder.addPoint(1.0F, k, u);
        }

        return builder.build();
    }

    private static float mountainContinentalness(float f, float g, float h) {
        float i = 1.17F;
        float j = 0.46082947F;
        float k = 1.0F - (1.0F - g) * 0.5F;
        float l = 0.5F * (1.0F - g);
        float m = (f + 1.17F) * 0.46082947F;
        float n = m * k - l;
        return f < h ? Math.max(n, -0.2222F) : Math.max(n, 0.0F);
    }

    private static float calculateMountainRidgeZeroContinentalnessPoint(float f) {
        float g = 1.17F;
        float h = 0.46082947F;
        float i = 1.0F - (1.0F - f) * 0.5F;
        float j = 0.5F * (1.0F - f);
        return j / (0.46082947F * i) - 1.17F;
    }

    public static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> buildErosionOffsetSpline(
        I erosion,
        I ridgesFolded,
        float continentalness,
        float f,
        float g,
        float h,
        float i,
        float j,
        boolean bl,
        boolean bl2,
        ToFloatFunction<Float> amplifier
    ) {
        float k = 0.6F;
        float l = 0.5F;
        float m = 0.5F;
        CubicSpline<C, I> cubicSpline = buildMountainRidgeSplineWithPoints(ridgesFolded, Mth.lerp(h, 0.6F, 1.5F), bl2, amplifier);
        CubicSpline<C, I> cubicSpline2 = buildMountainRidgeSplineWithPoints(ridgesFolded, Mth.lerp(h, 0.6F, 1.0F), bl2, amplifier);
        CubicSpline<C, I> cubicSpline3 = buildMountainRidgeSplineWithPoints(ridgesFolded, h, bl2, amplifier);
        CubicSpline<C, I> cubicSpline4 = ridgeSpline(
            ridgesFolded, continentalness - 0.15F, 0.5F * h, Mth.lerp(0.5F, 0.5F, 0.5F) * h, 0.5F * h, 0.6F * h, 0.5F, amplifier
        );
        CubicSpline<C, I> cubicSpline5 = ridgeSpline(ridgesFolded, continentalness, i * h, f * h, 0.5F * h, 0.6F * h, 0.5F, amplifier);
        CubicSpline<C, I> cubicSpline6 = ridgeSpline(ridgesFolded, continentalness, i, i, f, g, 0.5F, amplifier);
        CubicSpline<C, I> cubicSpline7 = ridgeSpline(ridgesFolded, continentalness, i, i, f, g, 0.5F, amplifier);
        CubicSpline<C, I> cubicSpline8 = CubicSpline.<C, I>builder(ridgesFolded, amplifier)
            .addPoint(-1.0F, continentalness)
            .addPoint(-0.4F, cubicSpline6)
            .addPoint(0.0F, g + 0.07F)
            .build();
        CubicSpline<C, I> cubicSpline9 = ridgeSpline(ridgesFolded, -0.02F, j, j, f, g, 0.0F, amplifier);
        CubicSpline.Builder<C, I> builder = CubicSpline.<C, I>builder(erosion, amplifier)
            .addPoint(-0.85F, cubicSpline)
            .addPoint(-0.7F, cubicSpline2)
            .addPoint(-0.4F, cubicSpline3)
            .addPoint(-0.35F, cubicSpline4)
            .addPoint(-0.1F, cubicSpline5)
            .addPoint(0.2F, cubicSpline6);
        if (bl) {
            builder.addPoint(0.4F, cubicSpline7).addPoint(0.45F, cubicSpline8).addPoint(0.55F, cubicSpline8).addPoint(0.58F, cubicSpline7);
        }

        builder.addPoint(0.7F, cubicSpline9);
        return builder.build();
    }

    private static <C, I extends ToFloatFunction<C>> CubicSpline<C, I> ridgeSpline(
        I ridgesFolded, float continentalness, float f, float g, float h, float i, float j, ToFloatFunction<Float> amplifier
    ) {
        float k = Math.max(0.5F * (f - continentalness), j);
        float l = 5.0F * (g - f);
        return CubicSpline.<C, I>builder(ridgesFolded, amplifier)
            .addPoint(-1.0F, continentalness, k)
            .addPoint(-0.4F, f, Math.min(k, l))
            .addPoint(0.0F, g, l)
            .addPoint(0.4F, h, 2.0F * (h - g))
            .addPoint(1.0F, i, 0.7F * (i - h))
            .build();
    }
}
