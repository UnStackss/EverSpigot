package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Optional;
import javax.annotation.Nullable;

public class LastSeenMessagesValidator {
    private final int lastSeenCount;
    private final ObjectList<LastSeenTrackedEntry> trackedMessages = new ObjectArrayList<>();
    @Nullable
    private MessageSignature lastPendingMessage;

    public LastSeenMessagesValidator(int size) {
        this.lastSeenCount = size;

        for (int i = 0; i < size; i++) {
            this.trackedMessages.add(null);
        }
    }

    public void addPending(MessageSignature signature) {
        if (!signature.equals(this.lastPendingMessage)) {
            this.trackedMessages.add(new LastSeenTrackedEntry(signature, true));
            this.lastPendingMessage = signature;
        }
    }

    public int trackedMessagesCount() {
        return this.trackedMessages.size();
    }

    public boolean applyOffset(int index) {
        int i = this.trackedMessages.size() - this.lastSeenCount;
        if (index >= 0 && index <= i) {
            this.trackedMessages.removeElements(0, index);
            return true;
        } else {
            return false;
        }
    }

    public Optional<LastSeenMessages> applyUpdate(LastSeenMessages.Update acknowledgment) {
        if (!this.applyOffset(acknowledgment.offset())) {
            return Optional.empty();
        } else {
            ObjectList<MessageSignature> objectList = new ObjectArrayList<>(acknowledgment.acknowledged().cardinality());
            if (acknowledgment.acknowledged().length() > this.lastSeenCount) {
                return Optional.empty();
            } else {
                for (int i = 0; i < this.lastSeenCount; i++) {
                    boolean bl = acknowledgment.acknowledged().get(i);
                    LastSeenTrackedEntry lastSeenTrackedEntry = this.trackedMessages.get(i);
                    if (bl) {
                        if (lastSeenTrackedEntry == null) {
                            return Optional.empty();
                        }

                        this.trackedMessages.set(i, lastSeenTrackedEntry.acknowledge());
                        objectList.add(lastSeenTrackedEntry.signature());
                    } else {
                        if (lastSeenTrackedEntry != null && !lastSeenTrackedEntry.pending()) {
                            return Optional.empty();
                        }

                        this.trackedMessages.set(i, null);
                    }
                }

                return Optional.of(new LastSeenMessages(objectList));
            }
        }
    }
}
