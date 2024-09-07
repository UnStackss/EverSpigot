package net.minecraft.world.level.block.state.properties;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum StructureMode implements StringRepresentable {
    SAVE("save"),
    LOAD("load"),
    CORNER("corner"),
    DATA("data");

    private final String name;
    private final Component displayName;

    private StructureMode(final String name) {
        this.name = name;
        this.displayName = Component.translatable("structure_block.mode_info." + name);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public Component getDisplayName() {
        return this.displayName;
    }
}
