package net.minecraft.world.level.lighting;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jetbrains.annotations.VisibleForTesting;

public final class SkyLightEngine extends LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {
    private static final long REMOVE_TOP_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);
    private static final long ADD_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.increaseSkipOneDirection(15, false, Direction.UP);
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final ChunkSkyLightSources emptyChunkSources;

    public SkyLightEngine(LightChunkGetter chunkProvider) {
        this(chunkProvider, new SkyLightSectionStorage(chunkProvider));
    }

    @VisibleForTesting
    protected SkyLightEngine(LightChunkGetter chunkProvider, SkyLightSectionStorage lightStorage) {
        super(chunkProvider, lightStorage);
        this.emptyChunkSources = new ChunkSkyLightSources(chunkProvider.getLevel());
    }

    private static boolean isSourceLevel(int i) {
        return i == 15;
    }

    private int getLowestSourceY(int x, int z, int i) {
        ChunkSkyLightSources chunkSkyLightSources = this.getChunkSources(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return chunkSkyLightSources == null ? i : chunkSkyLightSources.getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
    }

    @Nullable
    private ChunkSkyLightSources getChunkSources(int chunkX, int chunkZ) {
        LightChunk lightChunk = this.chunkSource.getChunkForLighting(chunkX, chunkZ);
        return lightChunk != null ? lightChunk.getSkyLightSources() : null;
    }

    @Override
    protected void checkNode(long blockPos) {
        int i = BlockPos.getX(blockPos);
        int j = BlockPos.getY(blockPos);
        int k = BlockPos.getZ(blockPos);
        long l = SectionPos.blockToSection(blockPos);
        int m = this.storage.lightOnInSection(l) ? this.getLowestSourceY(i, k, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        if (m != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(i, k, m);
        }

        if (this.storage.storingLightForSection(l)) {
            boolean bl = j >= m;
            if (bl) {
                this.enqueueDecrease(blockPos, REMOVE_SKY_SOURCE_ENTRY);
                this.enqueueIncrease(blockPos, ADD_SKY_SOURCE_ENTRY);
            } else {
                int n = this.storage.getStoredLevel(blockPos);
                if (n > 0) {
                    this.storage.setStoredLevel(blockPos, 0);
                    this.enqueueDecrease(blockPos, LightEngine.QueueEntry.decreaseAllDirections(n));
                } else {
                    this.enqueueDecrease(blockPos, PULL_LIGHT_IN_ENTRY);
                }
            }
        }
    }

    private void updateSourcesInColumn(int i, int j, int k) {
        int l = SectionPos.sectionToBlockCoord(this.storage.getBottomSectionY());
        this.removeSourcesBelow(i, j, k, l);
        this.addSourcesAbove(i, j, k, l);
    }

    private void removeSourcesBelow(int x, int z, int i, int j) {
        if (i > j) {
            int k = SectionPos.blockToSectionCoord(x);
            int l = SectionPos.blockToSectionCoord(z);
            int m = i - 1;

            for (int n = SectionPos.blockToSectionCoord(m); this.storage.hasLightDataAtOrBelow(n); n--) {
                if (this.storage.storingLightForSection(SectionPos.asLong(k, n, l))) {
                    int o = SectionPos.sectionToBlockCoord(n);
                    int p = o + 15;

                    for (int q = Math.min(p, m); q >= o; q--) {
                        long r = BlockPos.asLong(x, q, z);
                        if (!isSourceLevel(this.storage.getStoredLevel(r))) {
                            return;
                        }

                        this.storage.setStoredLevel(r, 0);
                        this.enqueueDecrease(r, q == i - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    private void addSourcesAbove(int i, int j, int k, int l) {
        int m = SectionPos.blockToSectionCoord(i);
        int n = SectionPos.blockToSectionCoord(j);
        int o = Math.max(
            Math.max(this.getLowestSourceY(i - 1, j, Integer.MIN_VALUE), this.getLowestSourceY(i + 1, j, Integer.MIN_VALUE)),
            Math.max(this.getLowestSourceY(i, j - 1, Integer.MIN_VALUE), this.getLowestSourceY(i, j + 1, Integer.MIN_VALUE))
        );
        int p = Math.max(k, l);

        for (long q = SectionPos.asLong(m, SectionPos.blockToSectionCoord(p), n); !this.storage.isAboveData(q); q = SectionPos.offset(q, Direction.UP)) {
            if (this.storage.storingLightForSection(q)) {
                int r = SectionPos.sectionToBlockCoord(SectionPos.y(q));
                int s = r + 15;

                for (int t = Math.max(r, p); t <= s; t++) {
                    long u = BlockPos.asLong(i, t, j);
                    if (isSourceLevel(this.storage.getStoredLevel(u))) {
                        return;
                    }

                    this.storage.setStoredLevel(u, 15);
                    if (t < o || t == k) {
                        this.enqueueIncrease(u, ADD_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    @Override
    protected void propagateIncrease(long blockPos, long l, int lightLevel) {
        BlockState blockState = null;
        int i = this.countEmptySectionsBelowIfAtBorder(blockPos);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(l, direction)) {
                long m = BlockPos.offset(blockPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(m))) {
                    int j = this.storage.getStoredLevel(m);
                    int k = lightLevel - 1;
                    if (k > j) {
                        this.mutablePos.set(m);
                        BlockState blockState2 = this.getState(this.mutablePos);
                        int n = lightLevel - this.getOpacity(blockState2, this.mutablePos);
                        if (n > j) {
                            if (blockState == null) {
                                blockState = LightEngine.QueueEntry.isFromEmptyShape(l)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(blockPos));
                            }

                            if (!this.shapeOccludes(blockPos, blockState, m, blockState2, direction)) {
                                this.storage.setStoredLevel(m, n);
                                if (n > 1) {
                                    this.enqueueIncrease(
                                        m, LightEngine.QueueEntry.increaseSkipOneDirection(n, isEmptyShape(blockState2), direction.getOpposite())
                                    );
                                }

                                this.propagateFromEmptySections(m, direction, n, true, i);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long blockPos, long l) {
        int i = this.countEmptySectionsBelowIfAtBorder(blockPos);
        int j = LightEngine.QueueEntry.getFromLevel(l);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(l, direction)) {
                long m = BlockPos.offset(blockPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(m))) {
                    int k = this.storage.getStoredLevel(m);
                    if (k != 0) {
                        if (k <= j - 1) {
                            this.storage.setStoredLevel(m, 0);
                            this.enqueueDecrease(m, LightEngine.QueueEntry.decreaseSkipOneDirection(k, direction.getOpposite()));
                            this.propagateFromEmptySections(m, direction, k, false, i);
                        } else {
                            this.enqueueIncrease(m, LightEngine.QueueEntry.increaseOnlyOneDirection(k, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private int countEmptySectionsBelowIfAtBorder(long blockPos) {
        int i = BlockPos.getY(blockPos);
        int j = SectionPos.sectionRelative(i);
        if (j != 0) {
            return 0;
        } else {
            int k = BlockPos.getX(blockPos);
            int l = BlockPos.getZ(blockPos);
            int m = SectionPos.sectionRelative(k);
            int n = SectionPos.sectionRelative(l);
            if (m != 0 && m != 15 && n != 0 && n != 15) {
                return 0;
            } else {
                int o = SectionPos.blockToSectionCoord(k);
                int p = SectionPos.blockToSectionCoord(i);
                int q = SectionPos.blockToSectionCoord(l);
                int r = 0;

                while (!this.storage.storingLightForSection(SectionPos.asLong(o, p - r - 1, q)) && this.storage.hasLightDataAtOrBelow(p - r - 1)) {
                    r++;
                }

                return r;
            }
        }
    }

    private void propagateFromEmptySections(long blockPos, Direction direction, int lightLevel, boolean bl, int i) {
        if (i != 0) {
            int j = BlockPos.getX(blockPos);
            int k = BlockPos.getZ(blockPos);
            if (crossedSectionEdge(direction, SectionPos.sectionRelative(j), SectionPos.sectionRelative(k))) {
                int l = BlockPos.getY(blockPos);
                int m = SectionPos.blockToSectionCoord(j);
                int n = SectionPos.blockToSectionCoord(k);
                int o = SectionPos.blockToSectionCoord(l) - 1;
                int p = o - i + 1;

                while (o >= p) {
                    if (!this.storage.storingLightForSection(SectionPos.asLong(m, o, n))) {
                        o--;
                    } else {
                        int q = SectionPos.sectionToBlockCoord(o);

                        for (int r = 15; r >= 0; r--) {
                            long s = BlockPos.asLong(j, q + r, k);
                            if (bl) {
                                this.storage.setStoredLevel(s, lightLevel);
                                if (lightLevel > 1) {
                                    this.enqueueIncrease(s, LightEngine.QueueEntry.increaseSkipOneDirection(lightLevel, true, direction.getOpposite()));
                                }
                            } else {
                                this.storage.setStoredLevel(s, 0);
                                this.enqueueDecrease(s, LightEngine.QueueEntry.decreaseSkipOneDirection(lightLevel, direction.getOpposite()));
                            }
                        }

                        o--;
                    }
                }
            }
        }
    }

    private static boolean crossedSectionEdge(Direction direction, int localX, int localZ) {
        return switch (direction) {
            case NORTH -> localZ == 15;
            case SOUTH -> localZ == 0;
            case WEST -> localX == 15;
            case EAST -> localX == 0;
            default -> false;
        };
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean retainData) {
        super.setLightEnabled(pos, retainData);
        if (retainData) {
            ChunkSkyLightSources chunkSkyLightSources = Objects.requireNonNullElse(this.getChunkSources(pos.x, pos.z), this.emptyChunkSources);
            int i = chunkSkyLightSources.getHighestLowestSourceY() - 1;
            int j = SectionPos.blockToSectionCoord(i) + 1;
            long l = SectionPos.getZeroNode(pos.x, pos.z);
            int k = this.storage.getTopSectionY(l);
            int m = Math.max(this.storage.getBottomSectionY(), j);

            for (int n = k - 1; n >= m; n--) {
                DataLayer dataLayer = this.storage.getDataLayerToWrite(SectionPos.asLong(pos.x, n, pos.z));
                if (dataLayer != null && dataLayer.isEmpty()) {
                    dataLayer.fill(15);
                }
            }
        }
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        long l = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
        this.storage.setLightEnabled(l, true);
        ChunkSkyLightSources chunkSkyLightSources = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources2 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z - 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources3 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x, chunkPos.z + 1), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources4 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x - 1, chunkPos.z), this.emptyChunkSources);
        ChunkSkyLightSources chunkSkyLightSources5 = Objects.requireNonNullElse(this.getChunkSources(chunkPos.x + 1, chunkPos.z), this.emptyChunkSources);
        int i = this.storage.getTopSectionY(l);
        int j = this.storage.getBottomSectionY();
        int k = SectionPos.sectionToBlockCoord(chunkPos.x);
        int m = SectionPos.sectionToBlockCoord(chunkPos.z);

        for (int n = i - 1; n >= j; n--) {
            long o = SectionPos.asLong(chunkPos.x, n, chunkPos.z);
            DataLayer dataLayer = this.storage.getDataLayerToWrite(o);
            if (dataLayer != null) {
                int p = SectionPos.sectionToBlockCoord(n);
                int q = p + 15;
                boolean bl = false;

                for (int r = 0; r < 16; r++) {
                    for (int s = 0; s < 16; s++) {
                        int t = chunkSkyLightSources.getLowestSourceY(s, r);
                        if (t <= q) {
                            int u = r == 0 ? chunkSkyLightSources2.getLowestSourceY(s, 15) : chunkSkyLightSources.getLowestSourceY(s, r - 1);
                            int v = r == 15 ? chunkSkyLightSources3.getLowestSourceY(s, 0) : chunkSkyLightSources.getLowestSourceY(s, r + 1);
                            int w = s == 0 ? chunkSkyLightSources4.getLowestSourceY(15, r) : chunkSkyLightSources.getLowestSourceY(s - 1, r);
                            int x = s == 15 ? chunkSkyLightSources5.getLowestSourceY(0, r) : chunkSkyLightSources.getLowestSourceY(s + 1, r);
                            int y = Math.max(Math.max(u, v), Math.max(w, x));

                            for (int z = q; z >= Math.max(p, t); z--) {
                                dataLayer.set(s, SectionPos.sectionRelative(z), r, 15);
                                if (z == t || z < y) {
                                    long aa = BlockPos.asLong(k + s, z, m + r);
                                    this.enqueueIncrease(aa, LightEngine.QueueEntry.increaseSkySourceInDirections(z == t, z < u, z < v, z < w, z < x));
                                }
                            }

                            if (t < p) {
                                bl = true;
                            }
                        }
                    }
                }

                if (!bl) {
                    break;
                }
            }
        }
    }
}
