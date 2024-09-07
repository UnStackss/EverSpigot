package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.util.StringRepresentable;

public interface SlotRange extends StringRepresentable {
    IntList slots();

    default int size() {
        return this.slots().size();
    }

    static SlotRange of(String name, IntList slotIds) {
        return new SlotRange() {
            @Override
            public IntList slots() {
                return slotIds;
            }

            @Override
            public String getSerializedName() {
                return name;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
