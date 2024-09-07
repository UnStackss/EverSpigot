package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.withDefaultNamespace(id));
    }

    static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(
        CustomPacketPayload.FallbackProvider<B> unknownCodecFactory, List<CustomPacketPayload.TypeAndCodec<? super B, ?>> types
    ) {
        final Map<ResourceLocation, StreamCodec<? super B, ? extends CustomPacketPayload>> map = types.stream()
            .collect(Collectors.toUnmodifiableMap(type -> type.type().id(), CustomPacketPayload.TypeAndCodec::codec));
        return new StreamCodec<B, CustomPacketPayload>() {
            private StreamCodec<? super B, ? extends CustomPacketPayload> findCodec(ResourceLocation id) {
                StreamCodec<? super B, ? extends CustomPacketPayload> streamCodec = map.get(id);
                return streamCodec != null ? streamCodec : unknownCodecFactory.create(id);
            }

            private <T extends CustomPacketPayload> void writeCap(B value, CustomPacketPayload.Type<T> id, CustomPacketPayload payload) {
                value.writeResourceLocation(id.id());
                StreamCodec<B, T> streamCodec = this.findCodec(id.id);
                streamCodec.encode(value, (T)payload);
            }

            @Override
            public void encode(B friendlyByteBuf, CustomPacketPayload customPacketPayload) {
                this.writeCap(friendlyByteBuf, customPacketPayload.type(), customPacketPayload);
            }

            @Override
            public CustomPacketPayload decode(B friendlyByteBuf) {
                ResourceLocation resourceLocation = friendlyByteBuf.readResourceLocation();
                return (CustomPacketPayload)this.findCodec(resourceLocation).decode(friendlyByteBuf);
            }
        };
    }

    public interface FallbackProvider<B extends FriendlyByteBuf> {
        StreamCodec<B, ? extends CustomPacketPayload> create(ResourceLocation id);
    }

    public static record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }

    public static record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<B, T> codec) {
    }
}
