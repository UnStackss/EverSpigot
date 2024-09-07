package net.minecraft.network.protocol.common.custom;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record StructuresDebugPayload(ResourceKey<Level> dimension, BoundingBox mainBB, List<StructuresDebugPayload.PieceInfo> pieces)
    implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, StructuresDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        StructuresDebugPayload::write, StructuresDebugPayload::new
    );
    public static final CustomPacketPayload.Type<StructuresDebugPayload> TYPE = CustomPacketPayload.createType("debug/structures");

    private StructuresDebugPayload(FriendlyByteBuf buf) {
        this(buf.readResourceKey(Registries.DIMENSION), readBoundingBox(buf), buf.readList(StructuresDebugPayload.PieceInfo::new));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeResourceKey(this.dimension);
        writeBoundingBox(buf, this.mainBB);
        buf.writeCollection(this.pieces, (buf2, piece) -> piece.write(buf));
    }

    @Override
    public CustomPacketPayload.Type<StructuresDebugPayload> type() {
        return TYPE;
    }

    static BoundingBox readBoundingBox(FriendlyByteBuf buf) {
        return new BoundingBox(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    static void writeBoundingBox(FriendlyByteBuf buf, BoundingBox box) {
        buf.writeInt(box.minX());
        buf.writeInt(box.minY());
        buf.writeInt(box.minZ());
        buf.writeInt(box.maxX());
        buf.writeInt(box.maxY());
        buf.writeInt(box.maxZ());
    }

    public static record PieceInfo(BoundingBox boundingBox, boolean isStart) {
        public PieceInfo(FriendlyByteBuf buf) {
            this(StructuresDebugPayload.readBoundingBox(buf), buf.readBoolean());
        }

        public void write(FriendlyByteBuf buf) {
            StructuresDebugPayload.writeBoundingBox(buf, this.boundingBox);
            buf.writeBoolean(this.isStart);
        }
    }
}
