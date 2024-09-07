package net.minecraft.util.debugchart;

public interface SampleLogger {
    void logFullSample(long[] values);

    void logSample(long value);

    void logPartialSample(long value, int column);
}
