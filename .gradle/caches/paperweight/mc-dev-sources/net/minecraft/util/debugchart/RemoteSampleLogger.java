package net.minecraft.util.debugchart;

import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;

public class RemoteSampleLogger extends AbstractSampleLogger {
    private final DebugSampleSubscriptionTracker subscriptionTracker;
    private final RemoteDebugSampleType sampleType;

    public RemoteSampleLogger(int size, DebugSampleSubscriptionTracker tracker, RemoteDebugSampleType type) {
        this(size, tracker, type, new long[size]);
    }

    public RemoteSampleLogger(int size, DebugSampleSubscriptionTracker tracker, RemoteDebugSampleType type, long[] defaults) {
        super(size, defaults);
        this.subscriptionTracker = tracker;
        this.sampleType = type;
    }

    @Override
    protected void useSample() {
        this.subscriptionTracker.broadcast(new ClientboundDebugSamplePacket((long[])this.sample.clone(), this.sampleType));
    }
}
