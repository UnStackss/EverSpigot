package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.BitSet;
import java.util.Objects;
import javax.annotation.Nullable;

public class LastSeenMessagesTracker {
    private final LastSeenTrackedEntry[] trackedMessages;
    private int tail;
    private int offset;
    @Nullable
    private MessageSignature lastTrackedMessage;

    public LastSeenMessagesTracker(int size) {
        this.trackedMessages = new LastSeenTrackedEntry[size];
    }

    public boolean addPending(MessageSignature signature, boolean displayed) {
        if (Objects.equals(signature, this.lastTrackedMessage)) {
            return false;
        } else {
            this.lastTrackedMessage = signature;
            this.addEntry(displayed ? new LastSeenTrackedEntry(signature, true) : null);
            return true;
        }
    }

    private void addEntry(@Nullable LastSeenTrackedEntry message) {
        int i = this.tail;
        this.tail = (i + 1) % this.trackedMessages.length;
        this.offset++;
        this.trackedMessages[i] = message;
    }

    public void ignorePending(MessageSignature signature) {
        for (int i = 0; i < this.trackedMessages.length; i++) {
            LastSeenTrackedEntry lastSeenTrackedEntry = this.trackedMessages[i];
            if (lastSeenTrackedEntry != null && lastSeenTrackedEntry.pending() && signature.equals(lastSeenTrackedEntry.signature())) {
                this.trackedMessages[i] = null;
                break;
            }
        }
    }

    public int getAndClearOffset() {
        int i = this.offset;
        this.offset = 0;
        return i;
    }

    public LastSeenMessagesTracker.Update generateAndApplyUpdate() {
        int i = this.getAndClearOffset();
        BitSet bitSet = new BitSet(this.trackedMessages.length);
        ObjectList<MessageSignature> objectList = new ObjectArrayList<>(this.trackedMessages.length);

        for (int j = 0; j < this.trackedMessages.length; j++) {
            int k = (this.tail + j) % this.trackedMessages.length;
            LastSeenTrackedEntry lastSeenTrackedEntry = this.trackedMessages[k];
            if (lastSeenTrackedEntry != null) {
                bitSet.set(j, true);
                objectList.add(lastSeenTrackedEntry.signature());
                this.trackedMessages[k] = lastSeenTrackedEntry.acknowledge();
            }
        }

        LastSeenMessages lastSeenMessages = new LastSeenMessages(objectList);
        LastSeenMessages.Update update = new LastSeenMessages.Update(i, bitSet);
        return new LastSeenMessagesTracker.Update(lastSeenMessages, update);
    }

    public int offset() {
        return this.offset;
    }

    public static record Update(LastSeenMessages lastSeen, LastSeenMessages.Update update) {
    }
}
