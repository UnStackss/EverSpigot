package net.minecraft.network.protocol.common.custom;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public record BeeDebugPayload(BeeDebugPayload.BeeInfo beeInfo) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BeeDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(BeeDebugPayload::write, BeeDebugPayload::new);
    public static final CustomPacketPayload.Type<BeeDebugPayload> TYPE = CustomPacketPayload.createType("debug/bee");

    private BeeDebugPayload(FriendlyByteBuf buf) {
        this(new BeeDebugPayload.BeeInfo(buf));
    }

    private void write(FriendlyByteBuf buf) {
        this.beeInfo.write(buf);
    }

    @Override
    public CustomPacketPayload.Type<BeeDebugPayload> type() {
        return TYPE;
    }

    public static record BeeInfo(
        UUID uuid,
        int id,
        Vec3 pos,
        @Nullable Path path,
        @Nullable BlockPos hivePos,
        @Nullable BlockPos flowerPos,
        int travelTicks,
        Set<String> goals,
        List<BlockPos> blacklistedHives
    ) {
        public BeeInfo(FriendlyByteBuf buf) {
            this(
                buf.readUUID(),
                buf.readInt(),
                buf.readVec3(),
                buf.readNullable(Path::createFromStream),
                buf.readNullable(BlockPos.STREAM_CODEC),
                buf.readNullable(BlockPos.STREAM_CODEC),
                buf.readInt(),
                buf.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                buf.readList(BlockPos.STREAM_CODEC)
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeInt(this.id);
            buf.writeVec3(this.pos);
            buf.writeNullable(this.path, (bufx, path) -> path.writeToStream(bufx));
            buf.writeNullable(this.hivePos, BlockPos.STREAM_CODEC);
            buf.writeNullable(this.flowerPos, BlockPos.STREAM_CODEC);
            buf.writeInt(this.travelTicks);
            buf.writeCollection(this.goals, FriendlyByteBuf::writeUtf);
            buf.writeCollection(this.blacklistedHives, BlockPos.STREAM_CODEC);
        }

        public boolean hasHive(BlockPos pos) {
            return Objects.equals(pos, this.hivePos);
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
