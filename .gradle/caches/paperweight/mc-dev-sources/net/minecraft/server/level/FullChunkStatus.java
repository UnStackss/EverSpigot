package net.minecraft.server.level;

public enum FullChunkStatus {
    INACCESSIBLE,
    FULL,
    BLOCK_TICKING,
    ENTITY_TICKING;

    public boolean isOrAfter(FullChunkStatus levelType) {
        return this.ordinal() >= levelType.ordinal();
    }
}
