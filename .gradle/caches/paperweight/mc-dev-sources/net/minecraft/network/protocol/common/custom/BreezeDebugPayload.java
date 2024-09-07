package net.minecraft.network.protocol.common.custom;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;

public record BreezeDebugPayload(BreezeDebugPayload.BreezeInfo breezeInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BreezeDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        BreezeDebugPayload::write, BreezeDebugPayload::new
    );
    public static final CustomPacketPayload.Type<BreezeDebugPayload> TYPE = CustomPacketPayload.createType("debug/breeze");

    private BreezeDebugPayload(FriendlyByteBuf buf) {
        this(new BreezeDebugPayload.BreezeInfo(buf));
    }

    private void write(FriendlyByteBuf buf) {
        this.breezeInfo.write(buf);
    }

    @Override
    public CustomPacketPayload.Type<BreezeDebugPayload> type() {
        return TYPE;
    }

    public static record BreezeInfo(UUID uuid, int id, Integer attackTarget, BlockPos jumpTarget) {
        public BreezeInfo(FriendlyByteBuf buf) {
            this(buf.readUUID(), buf.readInt(), buf.readNullable(FriendlyByteBuf::readInt), buf.readNullable(BlockPos.STREAM_CODEC));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeInt(this.id);
            buf.writeNullable(this.attackTarget, FriendlyByteBuf::writeInt);
            buf.writeNullable(this.jumpTarget, BlockPos.STREAM_CODEC);
        }

        public String generateName() {
            return DebugEntityNameGenerator.getEntityName(this.uuid);
        }

        @Override
        public String toString() {
            return this.generateName();
        }
    }
}
