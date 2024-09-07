package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record GameTestAddMarkerDebugPayload(BlockPos pos, int color, String text, int durationMs) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, GameTestAddMarkerDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        GameTestAddMarkerDebugPayload::write, GameTestAddMarkerDebugPayload::new
    );
    public static final CustomPacketPayload.Type<GameTestAddMarkerDebugPayload> TYPE = CustomPacketPayload.createType("debug/game_test_add_marker");

    private GameTestAddMarkerDebugPayload(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt(), buf.readUtf(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.color);
        buf.writeUtf(this.text);
        buf.writeInt(this.durationMs);
    }

    @Override
    public CustomPacketPayload.Type<GameTestAddMarkerDebugPayload> type() {
        return TYPE;
    }
}
