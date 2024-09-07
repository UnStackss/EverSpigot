package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

public class Vec3 implements Position {
    public static final Codec<Vec3> CODEC = Codec.DOUBLE
        .listOf()
        .comapFlatMap(
            coordinates -> Util.fixedSize((List<Double>)coordinates, 3).map(coords -> new Vec3(coords.get(0), coords.get(1), coords.get(2))),
            vec -> List.of(vec.x(), vec.y(), vec.z())
        );
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public final double x;
    public final double y;
    public final double z;

    public static Vec3 fromRGB24(int rgb) {
        double d = (double)(rgb >> 16 & 0xFF) / 255.0;
        double e = (double)(rgb >> 8 & 0xFF) / 255.0;
        double f = (double)(rgb & 0xFF) / 255.0;
        return new Vec3(d, e, f);
    }

    public static Vec3 atLowerCornerOf(Vec3i vec) {
        return new Vec3((double)vec.getX(), (double)vec.getY(), (double)vec.getZ());
    }

    public static Vec3 atLowerCornerWithOffset(Vec3i vec, double deltaX, double deltaY, double deltaZ) {
        return new Vec3((double)vec.getX() + deltaX, (double)vec.getY() + deltaY, (double)vec.getZ() + deltaZ);
    }

    public static Vec3 atCenterOf(Vec3i vec) {
        return atLowerCornerWithOffset(vec, 0.5, 0.5, 0.5);
    }

    public static Vec3 atBottomCenterOf(Vec3i vec) {
        return atLowerCornerWithOffset(vec, 0.5, 0.0, 0.5);
    }

    public static Vec3 upFromBottomCenterOf(Vec3i vec, double deltaY) {
        return atLowerCornerWithOffset(vec, 0.5, deltaY, 0.5);
    }

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3f vec) {
        this((double)vec.x(), (double)vec.y(), (double)vec.z());
    }

    public Vec3 vectorTo(Vec3 vec) {
        return new Vec3(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3 normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 1.0E-4 ? ZERO : new Vec3(this.x / d, this.y / d, this.z / d);
    }

    public double dot(Vec3 vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3 cross(Vec3 vec) {
        return new Vec3(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3 subtract(Vec3 vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3 subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3 add(Vec3 vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    public boolean closerThan(Position pos, double radius) {
        return this.distanceToSqr(pos.x(), pos.y(), pos.z()) < radius * radius;
    }

    public double distanceTo(Vec3 vec) {
        double d = vec.x - this.x;
        double e = vec.y - this.y;
        double f = vec.z - this.z;
        return Math.sqrt(d * d + e * e + f * f);
    }

    public double distanceToSqr(Vec3 vec) {
        double d = vec.x - this.x;
        double e = vec.y - this.y;
        double f = vec.z - this.z;
        return d * d + e * e + f * f;
    }

    public double distanceToSqr(double x, double y, double z) {
        double d = x - this.x;
        double e = y - this.y;
        double f = z - this.z;
        return d * d + e * e + f * f;
    }

    public boolean closerThan(Vec3 vec, double horizontalRange, double verticalRange) {
        double d = vec.x() - this.x;
        double e = vec.y() - this.y;
        double f = vec.z() - this.z;
        return Mth.lengthSquared(d, f) < Mth.square(horizontalRange) && Math.abs(e) < verticalRange;
    }

    public Vec3 scale(double value) {
        return this.multiply(value, value, value);
    }

    public Vec3 reverse() {
        return this.scale(-1.0);
    }

    public Vec3 multiply(Vec3 vec) {
        return this.multiply(vec.x, vec.y, vec.z);
    }

    public Vec3 multiply(double x, double y, double z) {
        return new Vec3(this.x * x, this.y * y, this.z * z);
    }

    public Vec3 offsetRandom(RandomSource random, float multiplier) {
        return this.add(
            (double)((random.nextFloat() - 0.5F) * multiplier),
            (double)((random.nextFloat() - 0.5F) * multiplier),
            (double)((random.nextFloat() - 0.5F) * multiplier)
        );
    }

    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public double horizontalDistance() {
        return Math.sqrt(this.x * this.x + this.z * this.z);
    }

    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof Vec3 vec3 && Double.compare(vec3.x, this.x) == 0 && Double.compare(vec3.y, this.y) == 0 && Double.compare(vec3.z, this.z) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.x);
        int i = (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.y);
        i = 31 * i + (int)(l ^ l >>> 32);
        l = Double.doubleToLongBits(this.z);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    public Vec3 lerp(Vec3 to, double delta) {
        return new Vec3(Mth.lerp(delta, this.x, to.x), Mth.lerp(delta, this.y, to.y), Mth.lerp(delta, this.z, to.z));
    }

    public Vec3 xRot(float angle) {
        float f = Mth.cos(angle);
        float g = Mth.sin(angle);
        double d = this.x;
        double e = this.y * (double)f + this.z * (double)g;
        double h = this.z * (double)f - this.y * (double)g;
        return new Vec3(d, e, h);
    }

    public Vec3 yRot(float angle) {
        float f = Mth.cos(angle);
        float g = Mth.sin(angle);
        double d = this.x * (double)f + this.z * (double)g;
        double e = this.y;
        double h = this.z * (double)f - this.x * (double)g;
        return new Vec3(d, e, h);
    }

    public Vec3 zRot(float angle) {
        float f = Mth.cos(angle);
        float g = Mth.sin(angle);
        double d = this.x * (double)f + this.y * (double)g;
        double e = this.y * (double)f - this.x * (double)g;
        double h = this.z;
        return new Vec3(d, e, h);
    }

    public static Vec3 directionFromRotation(Vec2 polar) {
        return directionFromRotation(polar.x, polar.y);
    }

    public static Vec3 directionFromRotation(float pitch, float yaw) {
        float f = Mth.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float g = Mth.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float h = -Mth.cos(-pitch * (float) (Math.PI / 180.0));
        float i = Mth.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3((double)(g * h), (double)i, (double)(f * h));
    }

    public Vec3 align(EnumSet<Direction.Axis> axes) {
        double d = axes.contains(Direction.Axis.X) ? (double)Mth.floor(this.x) : this.x;
        double e = axes.contains(Direction.Axis.Y) ? (double)Mth.floor(this.y) : this.y;
        double f = axes.contains(Direction.Axis.Z) ? (double)Mth.floor(this.z) : this.z;
        return new Vec3(d, e, f);
    }

    public double get(Direction.Axis axis) {
        return axis.choose(this.x, this.y, this.z);
    }

    public Vec3 with(Direction.Axis axis, double value) {
        double d = axis == Direction.Axis.X ? value : this.x;
        double e = axis == Direction.Axis.Y ? value : this.y;
        double f = axis == Direction.Axis.Z ? value : this.z;
        return new Vec3(d, e, f);
    }

    public Vec3 relative(Direction direction, double value) {
        Vec3i vec3i = direction.getNormal();
        return new Vec3(this.x + value * (double)vec3i.getX(), this.y + value * (double)vec3i.getY(), this.z + value * (double)vec3i.getZ());
    }

    @Override
    public final double x() {
        return this.x;
    }

    @Override
    public final double y() {
        return this.y;
    }

    @Override
    public final double z() {
        return this.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f((float)this.x, (float)this.y, (float)this.z);
    }
}
