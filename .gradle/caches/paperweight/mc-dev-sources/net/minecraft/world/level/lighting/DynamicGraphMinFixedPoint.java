package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.LongPredicate;
import net.minecraft.util.Mth;

public abstract class DynamicGraphMinFixedPoint {
    public static final long SOURCE = Long.MAX_VALUE;
    private static final int NO_COMPUTED_LEVEL = 255;
    protected final int levelCount;
    private final LeveledPriorityQueue priorityQueue;
    private final Long2ByteMap computedLevels;
    private volatile boolean hasWork;

    protected DynamicGraphMinFixedPoint(int levelCount, int expectedLevelSize, int expectedTotalSize) {
        if (levelCount >= 254) {
            throw new IllegalArgumentException("Level count must be < 254.");
        } else {
            this.levelCount = levelCount;
            this.priorityQueue = new LeveledPriorityQueue(levelCount, expectedLevelSize);
            this.computedLevels = new Long2ByteOpenHashMap(expectedTotalSize, 0.5F) {
                protected void rehash(int i) {
                    if (i > expectedTotalSize) {
                        super.rehash(i);
                    }
                }
            };
            this.computedLevels.defaultReturnValue((byte)-1);
        }
    }

    protected void removeFromQueue(long id) {
        int i = this.computedLevels.remove(id) & 255;
        if (i != 255) {
            int j = this.getLevel(id);
            int k = this.calculatePriority(j, i);
            this.priorityQueue.dequeue(id, k, this.levelCount);
            this.hasWork = !this.priorityQueue.isEmpty();
        }
    }

    public void removeIf(LongPredicate predicate) {
        LongList longList = new LongArrayList();
        this.computedLevels.keySet().forEach(l -> {
            if (predicate.test(l)) {
                longList.add(l);
            }
        });
        longList.forEach(this::removeFromQueue);
    }

    private int calculatePriority(int a, int b) {
        return Math.min(Math.min(a, b), this.levelCount - 1);
    }

    protected void checkNode(long id) {
        this.checkEdge(id, id, this.levelCount - 1, false);
    }

    protected void checkEdge(long sourceId, long id, int level, boolean decrease) {
        this.checkEdge(sourceId, id, level, this.getLevel(id), this.computedLevels.get(id) & 255, decrease);
        this.hasWork = !this.priorityQueue.isEmpty();
    }

    private void checkEdge(long sourceId, long id, int level, int currentLevel, int i, boolean decrease) {
        if (!this.isSource(id)) {
            level = Mth.clamp(level, 0, this.levelCount - 1);
            currentLevel = Mth.clamp(currentLevel, 0, this.levelCount - 1);
            boolean bl = i == 255;
            if (bl) {
                i = currentLevel;
            }

            int j;
            if (decrease) {
                j = Math.min(i, level);
            } else {
                j = Mth.clamp(this.getComputedLevel(id, sourceId, level), 0, this.levelCount - 1);
            }

            int l = this.calculatePriority(currentLevel, i);
            if (currentLevel != j) {
                int m = this.calculatePriority(currentLevel, j);
                if (l != m && !bl) {
                    this.priorityQueue.dequeue(id, l, m);
                }

                this.priorityQueue.enqueue(id, m);
                this.computedLevels.put(id, (byte)j);
            } else if (!bl) {
                this.priorityQueue.dequeue(id, l, this.levelCount);
                this.computedLevels.remove(id);
            }
        }
    }

    protected final void checkNeighbor(long sourceId, long targetId, int level, boolean decrease) {
        int i = this.computedLevels.get(targetId) & 255;
        int j = Mth.clamp(this.computeLevelFromNeighbor(sourceId, targetId, level), 0, this.levelCount - 1);
        if (decrease) {
            this.checkEdge(sourceId, targetId, j, this.getLevel(targetId), i, decrease);
        } else {
            boolean bl = i == 255;
            int k;
            if (bl) {
                k = Mth.clamp(this.getLevel(targetId), 0, this.levelCount - 1);
            } else {
                k = i;
            }

            if (j == k) {
                this.checkEdge(sourceId, targetId, this.levelCount - 1, bl ? k : this.getLevel(targetId), i, decrease);
            }
        }
    }

    protected final boolean hasWork() {
        return this.hasWork;
    }

    protected final int runUpdates(int maxSteps) {
        if (this.priorityQueue.isEmpty()) {
            return maxSteps;
        } else {
            while (!this.priorityQueue.isEmpty() && maxSteps > 0) {
                maxSteps--;
                long l = this.priorityQueue.removeFirstLong();
                int i = Mth.clamp(this.getLevel(l), 0, this.levelCount - 1);
                int j = this.computedLevels.remove(l) & 255;
                if (j < i) {
                    this.setLevel(l, j);
                    this.checkNeighborsAfterUpdate(l, j, true);
                } else if (j > i) {
                    this.setLevel(l, this.levelCount - 1);
                    if (j != this.levelCount - 1) {
                        this.priorityQueue.enqueue(l, this.calculatePriority(this.levelCount - 1, j));
                        this.computedLevels.put(l, (byte)j);
                    }

                    this.checkNeighborsAfterUpdate(l, i, false);
                }
            }

            this.hasWork = !this.priorityQueue.isEmpty();
            return maxSteps;
        }
    }

    public int getQueueSize() {
        return this.computedLevels.size();
    }

    protected boolean isSource(long id) {
        return id == Long.MAX_VALUE;
    }

    protected abstract int getComputedLevel(long id, long excludedId, int maxLevel);

    protected abstract void checkNeighborsAfterUpdate(long id, int level, boolean decrease);

    protected abstract int getLevel(long id);

    protected abstract void setLevel(long id, int level);

    protected abstract int computeLevelFromNeighbor(long sourceId, long targetId, int level);
}
