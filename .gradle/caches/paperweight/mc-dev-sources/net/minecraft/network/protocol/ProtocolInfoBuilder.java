package net.minecraft.network.protocol;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.codec.StreamCodec;

public class ProtocolInfoBuilder<T extends PacketListener, B extends ByteBuf> {
    final ConnectionProtocol protocol;
    final PacketFlow flow;
    private final List<ProtocolInfoBuilder.CodecEntry<T, ?, B>> codecs = new ArrayList<>();
    @Nullable
    private BundlerInfo bundlerInfo;

    public ProtocolInfoBuilder(ConnectionProtocol type, PacketFlow side) {
        this.protocol = type;
        this.flow = side;
    }

    public <P extends Packet<? super T>> ProtocolInfoBuilder<T, B> addPacket(PacketType<P> id, StreamCodec<? super B, P> codec) {
        this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(id, codec));
        return this;
    }

    public <P extends BundlePacket<? super T>, D extends BundleDelimiterPacket<? super T>> ProtocolInfoBuilder<T, B> withBundlePacket(
        PacketType<P> id, Function<Iterable<Packet<? super T>>, P> bundler, D splitter
    ) {
        StreamCodec<ByteBuf, D> streamCodec = StreamCodec.unit(splitter);
        PacketType<D> packetType = (PacketType<D>)splitter.type();
        this.codecs.add(new ProtocolInfoBuilder.CodecEntry<>(packetType, streamCodec));
        this.bundlerInfo = BundlerInfo.createForPacket(id, bundler, splitter);
        return this;
    }

    StreamCodec<ByteBuf, Packet<? super T>> buildPacketCodec(Function<ByteBuf, B> bufUpgrader, List<ProtocolInfoBuilder.CodecEntry<T, ?, B>> packetTypes) {
        ProtocolCodecBuilder<ByteBuf, T> protocolCodecBuilder = new ProtocolCodecBuilder<>(this.flow);

        for (ProtocolInfoBuilder.CodecEntry<T, ?, B> codecEntry : packetTypes) {
            codecEntry.addToBuilder(protocolCodecBuilder, bufUpgrader);
        }

        return protocolCodecBuilder.build();
    }

    public ProtocolInfo<T> build(Function<ByteBuf, B> bufUpgrader) {
        return new ProtocolInfoBuilder.Implementation<>(this.protocol, this.flow, this.buildPacketCodec(bufUpgrader, this.codecs), this.bundlerInfo);
    }

    public ProtocolInfo.Unbound<T, B> buildUnbound() {
        final List<ProtocolInfoBuilder.CodecEntry<T, ?, B>> list = List.copyOf(this.codecs);
        final BundlerInfo bundlerInfo = this.bundlerInfo;
        return new ProtocolInfo.Unbound<T, B>() {
            @Override
            public ProtocolInfo<T> bind(Function<ByteBuf, B> registryBinder) {
                return new ProtocolInfoBuilder.Implementation<>(
                    ProtocolInfoBuilder.this.protocol,
                    ProtocolInfoBuilder.this.flow,
                    ProtocolInfoBuilder.this.buildPacketCodec(registryBinder, list),
                    bundlerInfo
                );
            }

            @Override
            public ConnectionProtocol id() {
                return ProtocolInfoBuilder.this.protocol;
            }

            @Override
            public PacketFlow flow() {
                return ProtocolInfoBuilder.this.flow;
            }

            @Override
            public void listPackets(ProtocolInfo.Unbound.PacketVisitor callback) {
                for (int i = 0; i < list.size(); i++) {
                    ProtocolInfoBuilder.CodecEntry<T, ?, B> codecEntry = list.get(i);
                    callback.accept(codecEntry.type, i);
                }
            }
        };
    }

    private static <L extends PacketListener, B extends ByteBuf> ProtocolInfo.Unbound<L, B> protocol(
        ConnectionProtocol type, PacketFlow side, Consumer<ProtocolInfoBuilder<L, B>> registrar
    ) {
        ProtocolInfoBuilder<L, B> protocolInfoBuilder = new ProtocolInfoBuilder<>(type, side);
        registrar.accept(protocolInfoBuilder);
        return protocolInfoBuilder.buildUnbound();
    }

    public static <T extends ServerboundPacketListener, B extends ByteBuf> ProtocolInfo.Unbound<T, B> serverboundProtocol(
        ConnectionProtocol type, Consumer<ProtocolInfoBuilder<T, B>> registrar
    ) {
        return protocol(type, PacketFlow.SERVERBOUND, registrar);
    }

    public static <T extends ClientboundPacketListener, B extends ByteBuf> ProtocolInfo.Unbound<T, B> clientboundProtocol(
        ConnectionProtocol type, Consumer<ProtocolInfoBuilder<T, B>> registrar
    ) {
        return protocol(type, PacketFlow.CLIENTBOUND, registrar);
    }

    static record CodecEntry<T extends PacketListener, P extends Packet<? super T>, B extends ByteBuf>(PacketType<P> type, StreamCodec<? super B, P> serializer) {
        public void addToBuilder(ProtocolCodecBuilder<ByteBuf, T> builder, Function<ByteBuf, B> bufUpgrader) {
            StreamCodec<ByteBuf, P> streamCodec = this.serializer.mapStream(bufUpgrader);
            builder.add(this.type, streamCodec);
        }
    }

    static record Implementation<L extends PacketListener>(
        @Override ConnectionProtocol id,
        @Override PacketFlow flow,
        @Override StreamCodec<ByteBuf, Packet<? super L>> codec,
        @Nullable @Override BundlerInfo bundlerInfo
    ) implements ProtocolInfo<L> {
    }
}
