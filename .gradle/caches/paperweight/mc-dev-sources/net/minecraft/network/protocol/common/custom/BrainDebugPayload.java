package net.minecraft.network.protocol.common.custom;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public record BrainDebugPayload(BrainDebugPayload.BrainDump brainDump) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, BrainDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(
        BrainDebugPayload::write, BrainDebugPayload::new
    );
    public static final CustomPacketPayload.Type<BrainDebugPayload> TYPE = CustomPacketPayload.createType("debug/brain");

    private BrainDebugPayload(FriendlyByteBuf buf) {
        this(new BrainDebugPayload.BrainDump(buf));
    }

    private void write(FriendlyByteBuf buf) {
        this.brainDump.write(buf);
    }

    @Override
    public CustomPacketPayload.Type<BrainDebugPayload> type() {
        return TYPE;
    }

    public static record BrainDump(
        UUID uuid,
        int id,
        String name,
        String profession,
        int xp,
        float health,
        float maxHealth,
        Vec3 pos,
        String inventory,
        @Nullable Path path,
        boolean wantsGolem,
        int angerLevel,
        List<String> activities,
        List<String> behaviors,
        List<String> memories,
        List<String> gossips,
        Set<BlockPos> pois,
        Set<BlockPos> potentialPois
    ) {
        public BrainDump(FriendlyByteBuf buf) {
            this(
                buf.readUUID(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVec3(),
                buf.readUtf(),
                buf.readNullable(Path::createFromStream),
                buf.readBoolean(),
                buf.readInt(),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readList(FriendlyByteBuf::readUtf),
                buf.readCollection(HashSet::new, BlockPos.STREAM_CODEC),
                buf.readCollection(HashSet::new, BlockPos.STREAM_CODEC)
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(this.uuid);
            buf.writeInt(this.id);
            buf.writeUtf(this.name);
            buf.writeUtf(this.profession);
            buf.writeInt(this.xp);
            buf.writeFloat(this.health);
            buf.writeFloat(this.maxHealth);
            buf.writeVec3(this.pos);
            buf.writeUtf(this.inventory);
            buf.writeNullable(this.path, (bufx, path) -> path.writeToStream(bufx));
            buf.writeBoolean(this.wantsGolem);
            buf.writeInt(this.angerLevel);
            buf.writeCollection(this.activities, FriendlyByteBuf::writeUtf);
            buf.writeCollection(this.behaviors, FriendlyByteBuf::writeUtf);
            buf.writeCollection(this.memories, FriendlyByteBuf::writeUtf);
            buf.writeCollection(this.gossips, FriendlyByteBuf::writeUtf);
            buf.writeCollection(this.pois, BlockPos.STREAM_CODEC);
            buf.writeCollection(this.potentialPois, BlockPos.STREAM_CODEC);
        }

        public boolean hasPoi(BlockPos pos) {
            return this.pois.contains(pos);
        }

        public boolean hasPotentialPoi(BlockPos pos) {
            return this.potentialPois.contains(pos);
        }
    }
}
