package net.minecraft.util;

import java.util.Arrays;
import java.util.function.IntConsumer;
import org.apache.commons.lang3.Validate;

public class ZeroBitStorage implements BitStorage {
    public static final long[] RAW = new long[0];
    private final int size;

    public ZeroBitStorage(int size) {
        this.size = size;
    }

    @Override
    public final int getAndSet(int index, int value) { // Paper - Perf: Optimize SimpleBitStorage
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper - Perf: Optimize SimpleBitStorage
        //Validate.inclusiveBetween(0L, 0L, (long)value); // Paper - Perf: Optimize SimpleBitStorage
        return 0;
    }

    @Override
    public final void set(int index, int value) { // Paper - Perf: Optimize SimpleBitStorage
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper - Perf: Optimize SimpleBitStorage
        //Validate.inclusiveBetween(0L, 0L, (long)value); // Paper - Perf: Optimize SimpleBitStorage
    }

    @Override
    public final int get(int index) { // Paper - Perf: Optimize SimpleBitStorage
        //Validate.inclusiveBetween(0L, (long)(this.size - 1), (long)index); // Paper - Perf: Optimize SimpleBitStorage
        return 0;
    }

    @Override
    public long[] getRaw() {
        return RAW;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getBits() {
        return 0;
    }

    @Override
    public void getAll(IntConsumer action) {
        for (int i = 0; i < this.size; i++) {
            action.accept(0);
        }
    }

    @Override
    public void unpack(int[] out) {
        Arrays.fill(out, 0, this.size, 0);
    }

    @Override
    public BitStorage copy() {
        return this;
    }
}
