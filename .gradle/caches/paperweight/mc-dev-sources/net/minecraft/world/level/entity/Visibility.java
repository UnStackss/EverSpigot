package net.minecraft.world.level.entity;

import net.minecraft.server.level.FullChunkStatus;

public enum Visibility {
    HIDDEN(false, false),
    TRACKED(true, false),
    TICKING(true, true);

    private final boolean accessible;
    private final boolean ticking;

    private Visibility(final boolean tracked, final boolean tick) {
        this.accessible = tracked;
        this.ticking = tick;
    }

    public boolean isTicking() {
        return this.ticking;
    }

    public boolean isAccessible() {
        return this.accessible;
    }

    public static Visibility fromFullChunkStatus(FullChunkStatus levelType) {
        if (levelType.isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
            return TICKING;
        } else {
            return levelType.isOrAfter(FullChunkStatus.FULL) ? TRACKED : HIDDEN;
        }
    }
}
