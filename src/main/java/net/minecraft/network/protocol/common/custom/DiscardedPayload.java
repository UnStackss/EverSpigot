package net.minecraft.network.protocol.common.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record DiscardedPayload(ResourceLocation id, io.netty.buffer.ByteBuf data) implements CustomPacketPayload { // CraftBukkit - store data

    public static <T extends FriendlyByteBuf> StreamCodec<T, DiscardedPayload> codec(ResourceLocation id, int maxBytes) {
        return CustomPacketPayload.codec((discardedpayload, packetdataserializer) -> {
            packetdataserializer.writeBytes(discardedpayload.data); // CraftBukkit - serialize
        }, (packetdataserializer) -> {
            int j = packetdataserializer.readableBytes();

            if (j >= 0 && j <= maxBytes) {
                // CraftBukkit start
                return new DiscardedPayload(id, packetdataserializer.readBytes(j));
                // CraftBukkit end
            } else {
                throw new IllegalArgumentException("Payload may not be larger than " + maxBytes + " bytes");
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<DiscardedPayload> type() {
        return new CustomPacketPayload.Type<>(this.id);
    }
}
