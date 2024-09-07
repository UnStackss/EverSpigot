package com.mojang.math;

import org.joml.Quaternionf;
import org.joml.Vector3f;

@FunctionalInterface
public interface Axis {
    Axis XN = rad -> new Quaternionf().rotationX(-rad);
    Axis XP = rad -> new Quaternionf().rotationX(rad);
    Axis YN = rad -> new Quaternionf().rotationY(-rad);
    Axis YP = rad -> new Quaternionf().rotationY(rad);
    Axis ZN = rad -> new Quaternionf().rotationZ(-rad);
    Axis ZP = rad -> new Quaternionf().rotationZ(rad);

    static Axis of(Vector3f axis) {
        return rad -> new Quaternionf().rotationAxis(rad, axis);
    }

    Quaternionf rotation(float rad);

    default Quaternionf rotationDegrees(float deg) {
        return this.rotation(deg * (float) (Math.PI / 180.0));
    }
}
