package net.minecraft.world.entity;

import java.util.EnumSet;
import java.util.Set;

public enum RelativeMovement {
    X(0),
    Y(1),
    Z(2),
    Y_ROT(3),
    X_ROT(4);

    public static final Set<RelativeMovement> ALL = Set.of(values());
    public static final Set<RelativeMovement> ROTATION = Set.of(X_ROT, Y_ROT);
    private final int bit;

    private RelativeMovement(final int shift) {
        this.bit = shift;
    }

    private int getMask() {
        return 1 << this.bit;
    }

    private boolean isSet(int mask) {
        return (mask & this.getMask()) == this.getMask();
    }

    public static Set<RelativeMovement> unpack(int mask) {
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);

        for (RelativeMovement relativeMovement : values()) {
            if (relativeMovement.isSet(mask)) {
                set.add(relativeMovement);
            }
        }

        return set;
    }

    public static int pack(Set<RelativeMovement> flags) {
        int i = 0;

        for (RelativeMovement relativeMovement : flags) {
            i |= relativeMovement.getMask();
        }

        return i;
    }
}
