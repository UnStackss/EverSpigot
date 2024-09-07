package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public final class LongJumpUtil {
    public static Optional<Vec3> calculateJumpVectorForAngle(Mob entity, Vec3 jumpTarget, float maxVelocity, int angle, boolean requireClearPath) {
        Vec3 vec3 = entity.position();
        Vec3 vec32 = new Vec3(jumpTarget.x - vec3.x, 0.0, jumpTarget.z - vec3.z).normalize().scale(0.5);
        Vec3 vec33 = jumpTarget.subtract(vec32);
        Vec3 vec34 = vec33.subtract(vec3);
        float f = (float)angle * (float) Math.PI / 180.0F;
        double d = Math.atan2(vec34.z, vec34.x);
        double e = vec34.subtract(0.0, vec34.y, 0.0).lengthSqr();
        double g = Math.sqrt(e);
        double h = vec34.y;
        double i = entity.getGravity();
        double j = Math.sin((double)(2.0F * f));
        double k = Math.pow(Math.cos((double)f), 2.0);
        double l = Math.sin((double)f);
        double m = Math.cos((double)f);
        double n = Math.sin(d);
        double o = Math.cos(d);
        double p = e * i / (g * j - 2.0 * h * k);
        if (p < 0.0) {
            return Optional.empty();
        } else {
            double q = Math.sqrt(p);
            if (q > (double)maxVelocity) {
                return Optional.empty();
            } else {
                double r = q * m;
                double s = q * l;
                if (requireClearPath) {
                    int t = Mth.ceil(g / r) * 2;
                    double u = 0.0;
                    Vec3 vec35 = null;
                    EntityDimensions entityDimensions = entity.getDimensions(Pose.LONG_JUMPING);

                    for (int v = 0; v < t - 1; v++) {
                        u += g / (double)t;
                        double w = l / m * u - Math.pow(u, 2.0) * i / (2.0 * p * Math.pow(m, 2.0));
                        double x = u * o;
                        double y = u * n;
                        Vec3 vec36 = new Vec3(vec3.x + x, vec3.y + w, vec3.z + y);
                        if (vec35 != null && !isClearTransition(entity, entityDimensions, vec35, vec36)) {
                            return Optional.empty();
                        }

                        vec35 = vec36;
                    }
                }

                return Optional.of(new Vec3(r * o, s, r * n).scale(0.95F));
            }
        }
    }

    private static boolean isClearTransition(Mob entity, EntityDimensions dimensions, Vec3 prevPos, Vec3 nextPos) {
        Vec3 vec3 = nextPos.subtract(prevPos);
        double d = (double)Math.min(dimensions.width(), dimensions.height());
        int i = Mth.ceil(vec3.length() / d);
        Vec3 vec32 = vec3.normalize();
        Vec3 vec33 = prevPos;

        for (int j = 0; j < i; j++) {
            vec33 = j == i - 1 ? nextPos : vec33.add(vec32.scale(d * 0.9F));
            if (!entity.level().noCollision(entity, dimensions.makeBoundingBox(vec33))) {
                return false;
            }
        }

        return true;
    }
}
