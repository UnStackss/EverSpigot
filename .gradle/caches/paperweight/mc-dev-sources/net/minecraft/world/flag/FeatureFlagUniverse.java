package net.minecraft.world.flag;

public class FeatureFlagUniverse {
    private final String id;

    public FeatureFlagUniverse(String name) {
        this.id = name;
    }

    @Override
    public String toString() {
        return this.id;
    }
}
