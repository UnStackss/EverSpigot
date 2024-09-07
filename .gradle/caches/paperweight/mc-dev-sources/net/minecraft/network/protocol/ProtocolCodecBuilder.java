package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;

public class ProtocolCodecBuilder<B extends ByteBuf, L extends PacketListener> {
    private final IdDispatchCodec.Builder<B, Packet<? super L>, PacketType<? extends Packet<? super L>>> dispatchBuilder = IdDispatchCodec.builder(Packet::type);
    private final PacketFlow flow;

    public ProtocolCodecBuilder(PacketFlow side) {
        this.flow = side;
    }

    public <T extends Packet<? super L>> ProtocolCodecBuilder<B, L> add(PacketType<T> id, StreamCodec<? super B, T> codec) {
        if (id.flow() != this.flow) {
            throw new IllegalArgumentException("Invalid packet flow for packet " + id + ", expected " + this.flow.name());
        } else {
            this.dispatchBuilder.add(id, codec);
            return this;
        }
    }

    public StreamCodec<B, Packet<? super L>> build() {
        return this.dispatchBuilder.build();
    }
}
