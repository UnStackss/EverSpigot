package net.minecraft.network.protocol.common.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record HiveDebugPayload(HiveDebugPayload.HiveInfo hiveInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, HiveDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(HiveDebugPayload::write, HiveDebugPayload::new);
    public static final CustomPacketPayload.Type<HiveDebugPayload> TYPE = CustomPacketPayload.createType("debug/hive");

    private HiveDebugPayload(FriendlyByteBuf buf) {
        this(new HiveDebugPayload.HiveInfo(buf));
    }

    private void write(FriendlyByteBuf buf) {
        this.hiveInfo.write(buf);
    }

    @Override
    public CustomPacketPayload.Type<HiveDebugPayload> type() {
        return TYPE;
    }

    public static record HiveInfo(BlockPos pos, String hiveType, int occupantCount, int honeyLevel, boolean sedated) {
        public HiveInfo(FriendlyByteBuf buf) {
            this(buf.readBlockPos(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readBoolean());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(this.pos);
            buf.writeUtf(this.hiveType);
            buf.writeInt(this.occupantCount);
            buf.writeInt(this.honeyLevel);
            buf.writeBoolean(this.sedated);
        }
    }
}
