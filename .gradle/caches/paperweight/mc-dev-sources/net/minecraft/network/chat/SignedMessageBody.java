package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;

public record SignedMessageBody(String content, Instant timeStamp, long salt, LastSeenMessages lastSeen) {
    public static final MapCodec<SignedMessageBody> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.STRING.fieldOf("content").forGetter(SignedMessageBody::content),
                    ExtraCodecs.INSTANT_ISO8601.fieldOf("time_stamp").forGetter(SignedMessageBody::timeStamp),
                    Codec.LONG.fieldOf("salt").forGetter(SignedMessageBody::salt),
                    LastSeenMessages.CODEC.optionalFieldOf("last_seen", LastSeenMessages.EMPTY).forGetter(SignedMessageBody::lastSeen)
                )
                .apply(instance, SignedMessageBody::new)
    );

    public static SignedMessageBody unsigned(String content) {
        return new SignedMessageBody(content, Instant.now(), 0L, LastSeenMessages.EMPTY);
    }

    public void updateSignature(SignatureUpdater.Output updater) throws SignatureException {
        updater.update(Longs.toByteArray(this.salt));
        updater.update(Longs.toByteArray(this.timeStamp.getEpochSecond()));
        byte[] bs = this.content.getBytes(StandardCharsets.UTF_8);
        updater.update(Ints.toByteArray(bs.length));
        updater.update(bs);
        this.lastSeen.updateSignature(updater);
    }

    public SignedMessageBody.Packed pack(MessageSignatureCache storage) {
        return new SignedMessageBody.Packed(this.content, this.timeStamp, this.salt, this.lastSeen.pack(storage));
    }

    public static record Packed(String content, Instant timeStamp, long salt, LastSeenMessages.Packed lastSeen) {
        public Packed(FriendlyByteBuf buf) {
            this(buf.readUtf(256), buf.readInstant(), buf.readLong(), new LastSeenMessages.Packed(buf));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.content, 256);
            buf.writeInstant(this.timeStamp);
            buf.writeLong(this.salt);
            this.lastSeen.write(buf);
        }

        public Optional<SignedMessageBody> unpack(MessageSignatureCache storage) {
            return this.lastSeen.unpack(storage).map(lastSeenMessages -> new SignedMessageBody(this.content, this.timeStamp, this.salt, lastSeenMessages));
        }
    }
}
