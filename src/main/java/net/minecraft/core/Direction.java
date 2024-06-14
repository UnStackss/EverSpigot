package net.minecraft.core;

import com.google.common.collect.Iterators;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public enum Direction implements StringRepresentable, ca.spottedleaf.moonrise.patches.collisions.util.CollisionDirection { // Paper - optimise collisions
    DOWN(0, 1, -1, "down", Direction.AxisDirection.NEGATIVE, Direction.Axis.Y, new Vec3i(0, -1, 0)),
    UP(1, 0, -1, "up", Direction.AxisDirection.POSITIVE, Direction.Axis.Y, new Vec3i(0, 1, 0)),
    NORTH(2, 3, 2, "north", Direction.AxisDirection.NEGATIVE, Direction.Axis.Z, new Vec3i(0, 0, -1)),
    SOUTH(3, 2, 0, "south", Direction.AxisDirection.POSITIVE, Direction.Axis.Z, new Vec3i(0, 0, 1)),
    WEST(4, 5, 1, "west", Direction.AxisDirection.NEGATIVE, Direction.Axis.X, new Vec3i(-1, 0, 0)),
    EAST(5, 4, 3, "east", Direction.AxisDirection.POSITIVE, Direction.Axis.X, new Vec3i(1, 0, 0));

    public static final StringRepresentable.EnumCodec<Direction> CODEC = StringRepresentable.fromEnum(Direction::values);
    public static final Codec<Direction> VERTICAL_CODEC = CODEC.validate(Direction::verifyVertical);
    public static final IntFunction<Direction> BY_ID = ByIdMap.continuous(Direction::get3DDataValue, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    public static final StreamCodec<ByteBuf, Direction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Direction::get3DDataValue);
    private final int data3d;
    private final int oppositeIndex;
    private final int data2d;
    private final String name;
    private final Direction.Axis axis;
    private final Direction.AxisDirection axisDirection;
    private final Vec3i normal;
    private static final Direction[] VALUES = values();
    private static final Direction[] BY_3D_DATA = Arrays.stream(VALUES)
        .sorted(Comparator.comparingInt(direction -> direction.data3d))
        .toArray(Direction[]::new);
    private static final Direction[] BY_2D_DATA = Arrays.stream(VALUES)
        .filter(direction -> direction.getAxis().isHorizontal())
        .sorted(Comparator.comparingInt(direction -> direction.data2d))
        .toArray(Direction[]::new);

    // Paper start - Perf: Inline shift direction fields
    private final int adjX;
    private final int adjY;
    private final int adjZ;
    // Paper end - Perf: Inline shift direction fields
    // Paper start - optimise collisions
    private static final int RANDOM_OFFSET = 2017601568;
    private Direction opposite;
    private Quaternionf rotation;
    private int id;
    private int stepX;
    private int stepY;
    private int stepZ;

    private Quaternionf getRotationUncached() {
        switch ((Direction)(Object)this) {
            case DOWN: {
                return new Quaternionf().rotationX(3.1415927F);
            }
            case UP: {
                return new Quaternionf();
            }
            case NORTH: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, 3.1415927F);
            }
            case SOUTH: {
                return new Quaternionf().rotationX(1.5707964F);
            }
            case WEST: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, 1.5707964F);
            }
            case EAST: {
                return new Quaternionf().rotationXYZ(1.5707964F, 0.0F, -1.5707964F);
            }
            default: {
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public final int moonrise$uniqueId() {
        return this.id;
    }
    // Paper end - optimise collisions

    private Direction(
        final int id,
        final int idOpposite,
        final int idHorizontal,
        final String name,
        final Direction.AxisDirection direction,
        final Direction.Axis axis,
        final Vec3i vector
    ) {
        this.data3d = id;
        this.data2d = idHorizontal;
        this.oppositeIndex = idOpposite;
        this.name = name;
        this.axis = axis;
        this.axisDirection = direction;
        this.normal = vector;
        // Paper start - Perf: Inline shift direction fields
        this.adjX = vector.getX();
        this.adjY = vector.getY();
        this.adjZ = vector.getZ();
        // Paper end - Perf: Inline shift direction fields
    }

    public static Direction[] orderedByNearest(Entity entity) {
        float f = entity.getViewXRot(1.0F) * (float) (Math.PI / 180.0);
        float g = -entity.getViewYRot(1.0F) * (float) (Math.PI / 180.0);
        float h = Mth.sin(f);
        float i = Mth.cos(f);
        float j = Mth.sin(g);
        float k = Mth.cos(g);
        boolean bl = j > 0.0F;
        boolean bl2 = h < 0.0F;
        boolean bl3 = k > 0.0F;
        float l = bl ? j : -j;
        float m = bl2 ? -h : h;
        float n = bl3 ? k : -k;
        float o = l * i;
        float p = n * i;
        Direction direction = bl ? EAST : WEST;
        Direction direction2 = bl2 ? UP : DOWN;
        Direction direction3 = bl3 ? SOUTH : NORTH;
        if (l > n) {
            if (m > o) {
                return makeDirectionArray(direction2, direction, direction3);
            } else {
                return p > m ? makeDirectionArray(direction, direction3, direction2) : makeDirectionArray(direction, direction2, direction3);
            }
        } else if (m > p) {
            return makeDirectionArray(direction2, direction3, direction);
        } else {
            return o > m ? makeDirectionArray(direction3, direction, direction2) : makeDirectionArray(direction3, direction2, direction);
        }
    }

    private static Direction[] makeDirectionArray(Direction first, Direction second, Direction third) {
        return new Direction[]{first, second, third, third.getOpposite(), second.getOpposite(), first.getOpposite()};
    }

    public static Direction rotate(Matrix4f matrix, Direction direction) {
        Vec3i vec3i = direction.getNormal();
        Vector4f vector4f = matrix.transform(new Vector4f((float)vec3i.getX(), (float)vec3i.getY(), (float)vec3i.getZ(), 0.0F));
        return getNearest(vector4f.x(), vector4f.y(), vector4f.z());
    }

    public static Collection<Direction> allShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    public static Stream<Direction> stream() {
        return Stream.of(VALUES);
    }

    public Quaternionf getRotation() {
        // Paper start - optimise collisions
        try {
            return (Quaternionf)this.rotation.clone();
        } catch (final CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
        // Paper end - optimise collisions
    }

    public int get3DDataValue() {
        return this.data3d;
    }

    public int get2DDataValue() {
        return this.data2d;
    }

    public Direction.AxisDirection getAxisDirection() {
        return this.axisDirection;
    }

    public static Direction getFacingAxis(Entity entity, Direction.Axis axis) {
        return switch (axis) {
            case X -> EAST.isFacingAngle(entity.getViewYRot(1.0F)) ? EAST : WEST;
            case Y -> entity.getViewXRot(1.0F) < 0.0F ? UP : DOWN;
            case Z -> SOUTH.isFacingAngle(entity.getViewYRot(1.0F)) ? SOUTH : NORTH;
        };
    }

    public Direction getOpposite() {
        return this.opposite; // Paper - optimise collisions
    }

    public Direction getClockWise(Direction.Axis axis) {
        return switch (axis) {
            case X -> this != WEST && this != EAST ? this.getClockWiseX() : this;
            case Y -> this != UP && this != DOWN ? this.getClockWise() : this;
            case Z -> this != NORTH && this != SOUTH ? this.getClockWiseZ() : this;
        };
    }

    public Direction getCounterClockWise(Direction.Axis axis) {
        return switch (axis) {
            case X -> this != WEST && this != EAST ? this.getCounterClockWiseX() : this;
            case Y -> this != UP && this != DOWN ? this.getCounterClockWise() : this;
            case Z -> this != NORTH && this != SOUTH ? this.getCounterClockWiseZ() : this;
        };
    }

    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            case EAST -> SOUTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    private Direction getClockWiseX() {
        return switch (this) {
            case DOWN -> SOUTH;
            case UP -> NORTH;
            case NORTH -> DOWN;
            case SOUTH -> UP;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        };
    }

    private Direction getCounterClockWiseX() {
        return switch (this) {
            case DOWN -> NORTH;
            case UP -> SOUTH;
            case NORTH -> UP;
            case SOUTH -> DOWN;
            default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
        };
    }

    private Direction getClockWiseZ() {
        return switch (this) {
            case DOWN -> WEST;
            case UP -> EAST;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
            case WEST -> UP;
            case EAST -> DOWN;
        };
    }

    private Direction getCounterClockWiseZ() {
        return switch (this) {
            case DOWN -> EAST;
            case UP -> WEST;
            default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
            case WEST -> DOWN;
            case EAST -> UP;
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get CCW facing of " + this);
        };
    }

    public int getStepX() {
        return this.adjX; // Paper - Perf: Inline shift direction fields
    }

    public int getStepY() {
        return this.adjY; // Paper - Perf: Inline shift direction fields
    }

    public int getStepZ() {
        return this.adjZ; // Paper - Perf: Inline shift direction fields
    }

    public Vector3f step() {
        return new Vector3f((float)this.getStepX(), (float)this.getStepY(), (float)this.getStepZ());
    }

    public String getName() {
        return this.name;
    }

    public Direction.Axis getAxis() {
        return this.axis;
    }

    @Nullable
    public static Direction byName(@Nullable String name) {
        return CODEC.byName(name);
    }

    public static Direction from3DDataValue(int id) {
        return BY_3D_DATA[Mth.abs(id % BY_3D_DATA.length)];
    }

    public static Direction from2DDataValue(int value) {
        return BY_2D_DATA[Mth.abs(value % BY_2D_DATA.length)];
    }

    @Nullable
    public static Direction fromDelta(int x, int y, int z) {
        if (x == 0) {
            if (y == 0) {
                if (z > 0) {
                    return SOUTH;
                }

                if (z < 0) {
                    return NORTH;
                }
            } else if (z == 0) {
                if (y > 0) {
                    return UP;
                }

                return DOWN;
            }
        } else if (y == 0 && z == 0) {
            if (x > 0) {
                return EAST;
            }

            return WEST;
        }

        return null;
    }

    public static Direction fromYRot(double rotation) {
        return from2DDataValue(Mth.floor(rotation / 90.0 + 0.5) & 3);
    }

    public static Direction fromAxisAndDirection(Direction.Axis axis, Direction.AxisDirection direction) {
        return switch (axis) {
            case X -> direction == Direction.AxisDirection.POSITIVE ? EAST : WEST;
            case Y -> direction == Direction.AxisDirection.POSITIVE ? UP : DOWN;
            case Z -> direction == Direction.AxisDirection.POSITIVE ? SOUTH : NORTH;
        };
    }

    public float toYRot() {
        return (float)((this.data2d & 3) * 90);
    }

    public static Direction getRandom(RandomSource random) {
        return Util.getRandom(VALUES, random);
    }

    public static Direction getNearest(double x, double y, double z) {
        return getNearest((float)x, (float)y, (float)z);
    }

    public static Direction getNearest(float x, float y, float z) {
        Direction direction = NORTH;
        float f = Float.MIN_VALUE;

        for (Direction direction2 : VALUES) {
            float g = x * (float)direction2.normal.getX() + y * (float)direction2.normal.getY() + z * (float)direction2.normal.getZ();
            if (g > f) {
                f = g;
                direction = direction2;
            }
        }

        return direction;
    }

    public static Direction getNearest(Vec3 vec) {
        return getNearest(vec.x, vec.y, vec.z);
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    private static DataResult<Direction> verifyVertical(Direction direction) {
        return direction.getAxis().isVertical() ? DataResult.success(direction) : DataResult.error(() -> "Expected a vertical direction");
    }

    public static Direction get(Direction.AxisDirection direction, Direction.Axis axis) {
        for (Direction direction2 : VALUES) {
            if (direction2.getAxisDirection() == direction && direction2.getAxis() == axis) {
                return direction2;
            }
        }

        throw new IllegalArgumentException("No such direction: " + direction + " " + axis);
    }

    public Vec3i getNormal() {
        return this.normal;
    }

    public boolean isFacingAngle(float yaw) {
        float f = yaw * (float) (Math.PI / 180.0);
        float g = -Mth.sin(f);
        float h = Mth.cos(f);
        return (float)this.normal.getX() * g + (float)this.normal.getZ() * h > 0.0F;
    }

    public static enum Axis implements StringRepresentable, Predicate<Direction> {
        X("x") {
            @Override
            public int choose(int x, int y, int z) {
                return x;
            }

            @Override
            public double choose(double x, double y, double z) {
                return x;
            }
        },
        Y("y") {
            @Override
            public int choose(int x, int y, int z) {
                return y;
            }

            @Override
            public double choose(double x, double y, double z) {
                return y;
            }
        },
        Z("z") {
            @Override
            public int choose(int x, int y, int z) {
                return z;
            }

            @Override
            public double choose(double x, double y, double z) {
                return z;
            }
        };

        public static final Direction.Axis[] VALUES = values();
        public static final StringRepresentable.EnumCodec<Direction.Axis> CODEC = StringRepresentable.fromEnum(Direction.Axis::values);
        private final String name;

        Axis(final String name) {
            this.name = name;
        }

        @Nullable
        public static Direction.Axis byName(String name) {
            return CODEC.byName(name);
        }

        public String getName() {
            return this.name;
        }

        public boolean isVertical() {
            return this == Y;
        }

        public boolean isHorizontal() {
            return this == X || this == Z;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public static Direction.Axis getRandom(RandomSource random) {
            return Util.getRandom(VALUES, random);
        }

        @Override
        public boolean test(@Nullable Direction direction) {
            return direction != null && direction.getAxis() == this;
        }

        public Direction.Plane getPlane() {
            return switch (this) {
                case X, Z -> Direction.Plane.HORIZONTAL;
                case Y -> Direction.Plane.VERTICAL;
            };
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public abstract int choose(int x, int y, int z);

        public abstract double choose(double x, double y, double z);
    }

    public static enum AxisDirection {
        POSITIVE(1, "Towards positive"),
        NEGATIVE(-1, "Towards negative");

        private final int step;
        private final String name;

        private AxisDirection(final int offset, final String description) {
            this.step = offset;
            this.name = description;
        }

        public int getStep() {
            return this.step;
        }

        public String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public Direction.AxisDirection opposite() {
            return this == POSITIVE ? NEGATIVE : POSITIVE;
        }
    }

    public static enum Plane implements Iterable<Direction>, Predicate<Direction> {
        HORIZONTAL(new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}, new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}),
        VERTICAL(new Direction[]{Direction.UP, Direction.DOWN}, new Direction.Axis[]{Direction.Axis.Y});

        private final Direction[] faces;
        private final Direction.Axis[] axis;

        private Plane(final Direction[] facingArray, final Direction.Axis[] axisArray) {
            this.faces = facingArray;
            this.axis = axisArray;
        }

        public Direction getRandomDirection(RandomSource random) {
            return Util.getRandom(this.faces, random);
        }

        public Direction.Axis getRandomAxis(RandomSource random) {
            return Util.getRandom(this.axis, random);
        }

        @Override
        public boolean test(@Nullable Direction direction) {
            return direction != null && direction.getAxis().getPlane() == this;
        }

        @Override
        public Iterator<Direction> iterator() {
            return Iterators.forArray(this.faces);
        }

        public Stream<Direction> stream() {
            return Arrays.stream(this.faces);
        }

        public List<Direction> shuffledCopy(RandomSource random) {
            return Util.shuffledCopy(this.faces, random);
        }

        public int length() {
            return this.faces.length;
        }
    }

    // Paper start - optimise collisions
    static {
        for (final Direction direction : VALUES) {
            ((Direction)(Object)direction).opposite = from3DDataValue(((Direction)(Object)direction).oppositeIndex);
            ((Direction)(Object)direction).rotation = ((Direction)(Object)direction).getRotationUncached();
            ((Direction)(Object)direction).id = it.unimi.dsi.fastutil.HashCommon.murmurHash3(it.unimi.dsi.fastutil.HashCommon.murmurHash3(direction.ordinal() + RANDOM_OFFSET) + RANDOM_OFFSET);
            ((Direction)(Object)direction).stepX = ((Direction)(Object)direction).normal.getX();
            ((Direction)(Object)direction).stepY = ((Direction)(Object)direction).normal.getY();
            ((Direction)(Object)direction).stepZ = ((Direction)(Object)direction).normal.getZ();
        }
    }
    // Paper end - optimise collisions
}
