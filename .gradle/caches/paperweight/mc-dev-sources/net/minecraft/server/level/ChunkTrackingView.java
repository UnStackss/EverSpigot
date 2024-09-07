package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {
    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        public boolean contains(int x, int z, boolean includeEdge) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> consumer) {
        }
    };

    static ChunkTrackingView of(ChunkPos center, int viewDistance) {
        return new ChunkTrackingView.Positioned(center, viewDistance);
    }

    static void difference(ChunkTrackingView oldFilter, ChunkTrackingView newFilter, Consumer<ChunkPos> newlyIncluded, Consumer<ChunkPos> justRemoved) {
        if (!oldFilter.equals(newFilter)) {
            if (oldFilter instanceof ChunkTrackingView.Positioned positioned
                && newFilter instanceof ChunkTrackingView.Positioned positioned2
                && positioned.squareIntersects(positioned2)) {
                int i = Math.min(positioned.minX(), positioned2.minX());
                int j = Math.min(positioned.minZ(), positioned2.minZ());
                int k = Math.max(positioned.maxX(), positioned2.maxX());
                int l = Math.max(positioned.maxZ(), positioned2.maxZ());

                for (int m = i; m <= k; m++) {
                    for (int n = j; n <= l; n++) {
                        boolean bl = positioned.contains(m, n);
                        boolean bl2 = positioned2.contains(m, n);
                        if (bl != bl2) {
                            if (bl2) {
                                newlyIncluded.accept(new ChunkPos(m, n));
                            } else {
                                justRemoved.accept(new ChunkPos(m, n));
                            }
                        }
                    }
                }

                return;
            }

            oldFilter.forEach(justRemoved);
            newFilter.forEach(newlyIncluded);
        }
    }

    default boolean contains(ChunkPos pos) {
        return this.contains(pos.x, pos.z);
    }

    default boolean contains(int x, int z) {
        return this.contains(x, z, true);
    }

    boolean contains(int x, int z, boolean includeEdge);

    void forEach(Consumer<ChunkPos> consumer);

    default boolean isInViewDistance(int x, int z) {
        return this.contains(x, z, false);
    }

    static boolean isInViewDistance(int centerX, int centerZ, int viewDistance, int x, int z) {
        return isWithinDistance(centerX, centerZ, viewDistance, x, z, false);
    }

    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeEdge) {
        int i = Math.max(0, Math.abs(x - centerX) - 1);
        int j = Math.max(0, Math.abs(z - centerZ) - 1);
        long l = (long)Math.max(0, Math.max(i, j) - (includeEdge ? 1 : 0));
        long m = (long)Math.min(i, j);
        long n = m * m + l * l;
        int k = viewDistance * viewDistance;
        return n < (long)k;
    }

    public static record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {
        int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        @VisibleForTesting
        protected boolean squareIntersects(ChunkTrackingView.Positioned o) {
            return this.minX() <= o.maxX() && this.maxX() >= o.minX() && this.minZ() <= o.maxZ() && this.maxZ() >= o.minZ();
        }

        @Override
        public boolean contains(int x, int z, boolean includeEdge) {
            return ChunkTrackingView.isWithinDistance(this.center.x, this.center.z, this.viewDistance, x, z, includeEdge);
        }

        @Override
        public void forEach(Consumer<ChunkPos> consumer) {
            for (int i = this.minX(); i <= this.maxX(); i++) {
                for (int j = this.minZ(); j <= this.maxZ(); j++) {
                    if (this.contains(i, j)) {
                        consumer.accept(new ChunkPos(i, j));
                    }
                }
            }
        }
    }
}
