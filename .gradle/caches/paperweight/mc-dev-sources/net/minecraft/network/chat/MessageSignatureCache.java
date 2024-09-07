package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class MessageSignatureCache {
    public static final int NOT_FOUND = -1;
    private static final int DEFAULT_CAPACITY = 128;
    private final MessageSignature[] entries;

    public MessageSignatureCache(int maxEntries) {
        this.entries = new MessageSignature[maxEntries];
    }

    public static MessageSignatureCache createDefault() {
        return new MessageSignatureCache(128);
    }

    public int pack(MessageSignature signature) {
        for (int i = 0; i < this.entries.length; i++) {
            if (signature.equals(this.entries[i])) {
                return i;
            }
        }

        return -1;
    }

    @Nullable
    public MessageSignature unpack(int index) {
        return this.entries[index];
    }

    public void push(SignedMessageBody body, @Nullable MessageSignature signature) {
        List<MessageSignature> list = body.lastSeen().entries();
        ArrayDeque<MessageSignature> arrayDeque = new ArrayDeque<>(list.size() + 1);
        arrayDeque.addAll(list);
        if (signature != null) {
            arrayDeque.add(signature);
        }

        this.push(arrayDeque);
    }

    @VisibleForTesting
    void push(List<MessageSignature> signatures) {
        this.push(new ArrayDeque<>(signatures));
    }

    private void push(ArrayDeque<MessageSignature> deque) {
        Set<MessageSignature> set = new ObjectOpenHashSet<>(deque);

        for (int i = 0; !deque.isEmpty() && i < this.entries.length; i++) {
            MessageSignature messageSignature = this.entries[i];
            this.entries[i] = deque.removeLast();
            if (messageSignature != null && !set.contains(messageSignature)) {
                deque.addFirst(messageSignature);
            }
        }
    }
}
