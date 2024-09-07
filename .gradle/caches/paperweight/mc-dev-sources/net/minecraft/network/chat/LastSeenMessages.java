package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.Codec;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SignatureUpdater;

public record LastSeenMessages(List<MessageSignature> entries) {
    public static final Codec<LastSeenMessages> CODEC = MessageSignature.CODEC.listOf().xmap(LastSeenMessages::new, LastSeenMessages::entries);
    public static LastSeenMessages EMPTY = new LastSeenMessages(List.of());
    public static final int LAST_SEEN_MESSAGES_MAX_LENGTH = 20;

    public void updateSignature(SignatureUpdater.Output updater) throws SignatureException {
        updater.update(Ints.toByteArray(this.entries.size()));

        for (MessageSignature messageSignature : this.entries) {
            updater.update(messageSignature.bytes());
        }
    }

    public LastSeenMessages.Packed pack(MessageSignatureCache storage) {
        return new LastSeenMessages.Packed(this.entries.stream().map(signature -> signature.pack(storage)).toList());
    }

    public static record Packed(List<MessageSignature.Packed> entries) {
        public static final LastSeenMessages.Packed EMPTY = new LastSeenMessages.Packed(List.of());

        public Packed(FriendlyByteBuf buf) {
            this(buf.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 20), MessageSignature.Packed::read));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeCollection(this.entries, MessageSignature.Packed::write);
        }

        public Optional<LastSeenMessages> unpack(MessageSignatureCache storage) {
            List<MessageSignature> list = new ArrayList<>(this.entries.size());

            for (MessageSignature.Packed packed : this.entries) {
                Optional<MessageSignature> optional = packed.unpack(storage);
                if (optional.isEmpty()) {
                    return Optional.empty();
                }

                list.add(optional.get());
            }

            return Optional.of(new LastSeenMessages(list));
        }
    }

    public static record Update(int offset, BitSet acknowledged) {
        public Update(FriendlyByteBuf buf) {
            this(buf.readVarInt(), buf.readFixedBitSet(20));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(this.offset);
            buf.writeFixedBitSet(this.acknowledged, 20);
        }
    }
}
