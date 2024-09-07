package net.minecraft.util.debugchart;

public abstract class AbstractSampleLogger implements SampleLogger {
    protected final long[] defaults;
    protected final long[] sample;

    protected AbstractSampleLogger(int size, long[] defaults) {
        if (defaults.length != size) {
            throw new IllegalArgumentException("defaults have incorrect length of " + defaults.length);
        } else {
            this.sample = new long[size];
            this.defaults = defaults;
        }
    }

    @Override
    public void logFullSample(long[] values) {
        System.arraycopy(values, 0, this.sample, 0, values.length);
        this.useSample();
        this.resetSample();
    }

    @Override
    public void logSample(long value) {
        this.sample[0] = value;
        this.useSample();
        this.resetSample();
    }

    @Override
    public void logPartialSample(long value, int column) {
        if (column >= 1 && column < this.sample.length) {
            this.sample[column] = value;
        } else {
            throw new IndexOutOfBoundsException(column + " out of bounds for dimensions " + this.sample.length);
        }
    }

    protected abstract void useSample();

    protected void resetSample() {
        System.arraycopy(this.defaults, 0, this.sample, 0, this.defaults.length);
    }
}
