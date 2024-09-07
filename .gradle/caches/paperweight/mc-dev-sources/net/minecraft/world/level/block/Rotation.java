package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

public enum Rotation implements StringRepresentable {
    NONE("none", OctahedralGroup.IDENTITY),
    CLOCKWISE_90("clockwise_90", OctahedralGroup.ROT_90_Y_NEG),
    CLOCKWISE_180("180", OctahedralGroup.ROT_180_FACE_XZ),
    COUNTERCLOCKWISE_90("counterclockwise_90", OctahedralGroup.ROT_90_Y_POS);

    public static final Codec<Rotation> CODEC = StringRepresentable.fromEnum(Rotation::values);
    private final String id;
    private final OctahedralGroup rotation;

    private Rotation(final String id, final OctahedralGroup directionTransformation) {
        this.id = id;
        this.rotation = directionTransformation;
    }

    public Rotation getRotated(Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                switch (this) {
                    case NONE:
                        return CLOCKWISE_180;
                    case CLOCKWISE_90:
                        return COUNTERCLOCKWISE_90;
                    case CLOCKWISE_180:
                        return NONE;
                    case COUNTERCLOCKWISE_90:
                        return CLOCKWISE_90;
                }
            case COUNTERCLOCKWISE_90:
                switch (this) {
                    case NONE:
                        return COUNTERCLOCKWISE_90;
                    case CLOCKWISE_90:
                        return NONE;
                    case CLOCKWISE_180:
                        return CLOCKWISE_90;
                    case COUNTERCLOCKWISE_90:
                        return CLOCKWISE_180;
                }
            case CLOCKWISE_90:
                switch (this) {
                    case NONE:
                        return CLOCKWISE_90;
                    case CLOCKWISE_90:
                        return CLOCKWISE_180;
                    case CLOCKWISE_180:
                        return COUNTERCLOCKWISE_90;
                    case COUNTERCLOCKWISE_90:
                        return NONE;
                }
            default:
                return this;
        }
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Direction rotate(Direction direction) {
        if (direction.getAxis() == Direction.Axis.Y) {
            return direction;
        } else {
            switch (this) {
                case CLOCKWISE_90:
                    return direction.getClockWise();
                case CLOCKWISE_180:
                    return direction.getOpposite();
                case COUNTERCLOCKWISE_90:
                    return direction.getCounterClockWise();
                default:
                    return direction;
            }
        }
    }

    public int rotate(int rotation, int fullTurn) {
        switch (this) {
            case CLOCKWISE_90:
                return (rotation + fullTurn / 4) % fullTurn;
            case CLOCKWISE_180:
                return (rotation + fullTurn / 2) % fullTurn;
            case COUNTERCLOCKWISE_90:
                return (rotation + fullTurn * 3 / 4) % fullTurn;
            default:
                return rotation;
        }
    }

    public static Rotation getRandom(RandomSource random) {
        return Util.getRandom(values(), random);
    }

    public static List<Rotation> getShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
