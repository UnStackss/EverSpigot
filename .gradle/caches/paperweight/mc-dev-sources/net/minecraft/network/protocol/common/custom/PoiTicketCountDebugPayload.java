package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PoiTicketCountDebugPayload(BlockPos pos, int freeTicketCount) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, PoiTicketCountDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        PoiTicketCountDebugPayload::write, PoiTicketCountDebugPayload::new
    );
    public static final CustomPacketPayload.Type<PoiTicketCountDebugPayload> TYPE = CustomPacketPayload.createType("debug/poi_ticket_count");

    private PoiTicketCountDebugPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.freeTicketCount);
    }

    @Override
    public CustomPacketPayload.Type<PoiTicketCountDebugPayload> type() {
        return TYPE;
    }
}
