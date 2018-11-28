package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;

public interface Packet<T extends PacketListener> {
    PacketType<? extends Packet<T>> type();

    void handle(T listener);

    // Paper start
    default boolean hasLargePacketFallback() {
        return false;
    }

    /**
     * override {@link #hasLargePacketFallback()} to return true when overriding in subclasses
     */
    default boolean packetTooLarge(net.minecraft.network.Connection manager) {
        return false;
    }
    // Paper end

    default boolean isSkippable() {
        return false;
    }

    default boolean isTerminal() {
        return false;
    }

    static <B extends ByteBuf, T extends Packet<?>> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }
}
