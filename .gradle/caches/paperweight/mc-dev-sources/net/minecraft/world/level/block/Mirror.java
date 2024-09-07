package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum Mirror implements StringRepresentable {
    NONE("none", OctahedralGroup.IDENTITY),
    LEFT_RIGHT("left_right", OctahedralGroup.INVERT_Z),
    FRONT_BACK("front_back", OctahedralGroup.INVERT_X);

    public static final Codec<Mirror> CODEC = StringRepresentable.fromEnum(Mirror::values);
    private final String id;
    private final Component symbol;
    private final OctahedralGroup rotation;

    private Mirror(final String id, final OctahedralGroup directionTransformation) {
        this.id = id;
        this.symbol = Component.translatable("mirror." + id);
        this.rotation = directionTransformation;
    }

    public int mirror(int rotation, int fullTurn) {
        int i = fullTurn / 2;
        int j = rotation > i ? rotation - fullTurn : rotation;
        switch (this) {
            case LEFT_RIGHT:
                return (i - j + fullTurn) % fullTurn;
            case FRONT_BACK:
                return (fullTurn - j) % fullTurn;
            default:
                return rotation;
        }
    }

    public Rotation getRotation(Direction direction) {
        Direction.Axis axis = direction.getAxis();
        return (this != LEFT_RIGHT || axis != Direction.Axis.Z) && (this != FRONT_BACK || axis != Direction.Axis.X) ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    public Direction mirror(Direction direction) {
        if (this == FRONT_BACK && direction.getAxis() == Direction.Axis.X) {
            return direction.getOpposite();
        } else {
            return this == LEFT_RIGHT && direction.getAxis() == Direction.Axis.Z ? direction.getOpposite() : direction;
        }
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Component symbol() {
        return this.symbol;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
