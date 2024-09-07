package net.minecraft.util;

import net.minecraft.core.Direction;

public class SegmentedAnglePrecision {
    private final int mask;
    private final int precision;
    private final float degreeToAngle;
    private final float angleToDegree;

    public SegmentedAnglePrecision(int precision) {
        if (precision < 2) {
            throw new IllegalArgumentException("Precision cannot be less than 2 bits");
        } else if (precision > 30) {
            throw new IllegalArgumentException("Precision cannot be greater than 30 bits");
        } else {
            int i = 1 << precision;
            this.mask = i - 1;
            this.precision = precision;
            this.degreeToAngle = (float)i / 360.0F;
            this.angleToDegree = 360.0F / (float)i;
        }
    }

    public boolean isSameAxis(int alpha, int beta) {
        int i = this.getMask() >> 1;
        return (alpha & i) == (beta & i);
    }

    public int fromDirection(Direction direction) {
        if (direction.getAxis().isVertical()) {
            return 0;
        } else {
            int i = direction.get2DDataValue();
            return i << this.precision - 2;
        }
    }

    public int fromDegreesWithTurns(float degrees) {
        return Math.round(degrees * this.degreeToAngle);
    }

    public int fromDegrees(float degrees) {
        return this.normalize(this.fromDegreesWithTurns(degrees));
    }

    public float toDegreesWithTurns(int rotation) {
        return (float)rotation * this.angleToDegree;
    }

    public float toDegrees(int rotation) {
        float f = this.toDegreesWithTurns(this.normalize(rotation));
        return f >= 180.0F ? f - 360.0F : f;
    }

    public int normalize(int rotationBits) {
        return rotationBits & this.mask;
    }

    public int getMask() {
        return this.mask;
    }
}
