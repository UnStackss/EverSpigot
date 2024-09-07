package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;

public class NbtAccounter {
    private static final int MAX_STACK_DEPTH = 512;
    private final long quota;
    private long usage;
    private final int maxDepth;
    private int depth;

    public NbtAccounter(long maxBytes, int maxDepth) {
        this.quota = maxBytes;
        this.maxDepth = maxDepth;
    }

    public static NbtAccounter create(long maxBytes) {
        return new NbtAccounter(maxBytes, 512);
    }

    public static NbtAccounter unlimitedHeap() {
        return new NbtAccounter(Long.MAX_VALUE, 512);
    }

    public void accountBytes(long multiplier, long bytes) {
        this.accountBytes(multiplier * bytes);
    }

    public void accountBytes(long bytes) {
        if (this.usage + bytes > this.quota) {
            throw new NbtAccounterException(
                "Tried to read NBT tag that was too big; tried to allocate: " + this.usage + " + " + bytes + " bytes where max allowed: " + this.quota
            );
        } else {
            this.usage += bytes;
        }
    }

    public void pushDepth() {
        if (this.depth >= this.maxDepth) {
            throw new NbtAccounterException("Tried to read NBT tag with too high complexity, depth > " + this.maxDepth);
        } else {
            this.depth++;
        }
    }

    public void popDepth() {
        if (this.depth <= 0) {
            throw new NbtAccounterException("NBT-Accounter tried to pop stack-depth at top-level");
        } else {
            this.depth--;
        }
    }

    @VisibleForTesting
    public long getUsage() {
        return this.usage;
    }

    @VisibleForTesting
    public int getDepth() {
        return this.depth;
    }
}
