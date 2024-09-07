package net.minecraft.world.level.lighting;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

public class LeveledPriorityQueue {
    private final int levelCount;
    private final LongLinkedOpenHashSet[] queues;
    private int firstQueuedLevel;

    public LeveledPriorityQueue(int levelCount, int expectedLevelSize) {
        this.levelCount = levelCount;
        this.queues = new LongLinkedOpenHashSet[levelCount];

        for (int i = 0; i < levelCount; i++) {
            this.queues[i] = new LongLinkedOpenHashSet(expectedLevelSize, 0.5F) {
                protected void rehash(int i) {
                    if (i > expectedLevelSize) {
                        super.rehash(i);
                    }
                }
            };
        }

        this.firstQueuedLevel = levelCount;
    }

    public long removeFirstLong() {
        LongLinkedOpenHashSet longLinkedOpenHashSet = this.queues[this.firstQueuedLevel];
        long l = longLinkedOpenHashSet.removeFirstLong();
        if (longLinkedOpenHashSet.isEmpty()) {
            this.checkFirstQueuedLevel(this.levelCount);
        }

        return l;
    }

    public boolean isEmpty() {
        return this.firstQueuedLevel >= this.levelCount;
    }

    public void dequeue(long id, int level, int levelCount) {
        LongLinkedOpenHashSet longLinkedOpenHashSet = this.queues[level];
        longLinkedOpenHashSet.remove(id);
        if (longLinkedOpenHashSet.isEmpty() && this.firstQueuedLevel == level) {
            this.checkFirstQueuedLevel(levelCount);
        }
    }

    public void enqueue(long id, int level) {
        this.queues[level].add(id);
        if (this.firstQueuedLevel > level) {
            this.firstQueuedLevel = level;
        }
    }

    private void checkFirstQueuedLevel(int maxLevel) {
        int i = this.firstQueuedLevel;
        this.firstQueuedLevel = maxLevel;

        for (int j = i + 1; j < maxLevel; j++) {
            if (!this.queues[j].isEmpty()) {
                this.firstQueuedLevel = j;
                break;
            }
        }
    }
}
