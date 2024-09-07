package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.security.SignatureException;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;

public record SignedMessageLink(int index, UUID sender, UUID sessionId) {
    public static final Codec<SignedMessageLink> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("index").forGetter(SignedMessageLink::index),
                    UUIDUtil.CODEC.fieldOf("sender").forGetter(SignedMessageLink::sender),
                    UUIDUtil.CODEC.fieldOf("session_id").forGetter(SignedMessageLink::sessionId)
                )
                .apply(instance, SignedMessageLink::new)
    );

    public static SignedMessageLink unsigned(UUID sender) {
        return root(sender, Util.NIL_UUID);
    }

    public static SignedMessageLink root(UUID sender, UUID sessionId) {
        return new SignedMessageLink(0, sender, sessionId);
    }

    public void updateSignature(SignatureUpdater.Output updater) throws SignatureException {
        updater.update(UUIDUtil.uuidToByteArray(this.sender));
        updater.update(UUIDUtil.uuidToByteArray(this.sessionId));
        updater.update(Ints.toByteArray(this.index));
    }

    public boolean isDescendantOf(SignedMessageLink preceding) {
        return this.index > preceding.index() && this.sender.equals(preceding.sender()) && this.sessionId.equals(preceding.sessionId());
    }

    @Nullable
    public SignedMessageLink advance() {
        return this.index == Integer.MAX_VALUE ? null : new SignedMessageLink(this.index + 1, this.sender, this.sessionId);
    }
}
