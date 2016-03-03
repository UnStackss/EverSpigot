package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public class ClientboundLevelChunkPacketData {
    private static final int TWO_MEGABYTES = 2097152;
    private final CompoundTag heightmaps;
    private final byte[] buffer;
    private final List<ClientboundLevelChunkPacketData.BlockEntityInfo> blockEntitiesData;

    public ClientboundLevelChunkPacketData(LevelChunk chunk) {
        this.heightmaps = new CompoundTag();

        for (Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (entry.getKey().sendToClient()) {
                this.heightmaps.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        this.buffer = new byte[calculateChunkSize(chunk)];
        extractChunkData(new FriendlyByteBuf(this.getWriteBuffer()), chunk);
        this.blockEntitiesData = Lists.newArrayList();

        for (Entry<BlockPos, BlockEntity> entry2 : chunk.getBlockEntities().entrySet()) {
            this.blockEntitiesData.add(ClientboundLevelChunkPacketData.BlockEntityInfo.create(entry2.getValue()));
        }
    }

    public ClientboundLevelChunkPacketData(RegistryFriendlyByteBuf buf, int x, int z) {
        this.heightmaps = buf.readNbt();
        if (this.heightmaps == null) {
            throw new RuntimeException("Can't read heightmap in packet for [" + x + ", " + z + "]");
        } else {
            int i = buf.readVarInt();
            if (i > 2097152) {
                throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
            } else {
                this.buffer = new byte[i];
                buf.readBytes(this.buffer);
                this.blockEntitiesData = ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.decode(buf);
            }
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(this.heightmaps);
        buf.writeVarInt(this.buffer.length);
        buf.writeBytes(this.buffer);
        ClientboundLevelChunkPacketData.BlockEntityInfo.LIST_STREAM_CODEC.encode(buf, this.blockEntitiesData);
    }

    private static int calculateChunkSize(LevelChunk chunk) {
        int i = 0;

        for (LevelChunkSection levelChunkSection : chunk.getSections()) {
            i += levelChunkSection.getSerializedSize();
        }

        return i;
    }

    private ByteBuf getWriteBuffer() {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(this.buffer);
        byteBuf.writerIndex(0);
        return byteBuf;
    }

    public static void extractChunkData(FriendlyByteBuf buf, LevelChunk chunk) {
        for (LevelChunkSection levelChunkSection : chunk.getSections()) {
            levelChunkSection.write(buf);
        }
    }

    public Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> getBlockEntitiesTagsConsumer(int x, int z) {
        return visitor -> this.getBlockEntitiesTags(visitor, x, z);
    }

    private void getBlockEntitiesTags(ClientboundLevelChunkPacketData.BlockEntityTagOutput consumer, int x, int z) {
        int i = 16 * x;
        int j = 16 * z;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntityInfo : this.blockEntitiesData) {
            int k = i + SectionPos.sectionRelative(blockEntityInfo.packedXZ >> 4);
            int l = j + SectionPos.sectionRelative(blockEntityInfo.packedXZ);
            mutableBlockPos.set(k, blockEntityInfo.y, l);
            consumer.accept(mutableBlockPos, blockEntityInfo.type, blockEntityInfo.tag);
        }
    }

    public FriendlyByteBuf getReadBuffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.buffer));
    }

    public CompoundTag getHeightmaps() {
        return this.heightmaps;
    }

    static class BlockEntityInfo {
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLevelChunkPacketData.BlockEntityInfo> STREAM_CODEC = StreamCodec.ofMember(
            ClientboundLevelChunkPacketData.BlockEntityInfo::write, ClientboundLevelChunkPacketData.BlockEntityInfo::new
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, List<ClientboundLevelChunkPacketData.BlockEntityInfo>> LIST_STREAM_CODEC = STREAM_CODEC.apply(
            ByteBufCodecs.list()
        );
        final int packedXZ;
        final int y;
        final BlockEntityType<?> type;
        @Nullable
        final CompoundTag tag;

        private BlockEntityInfo(int localXz, int y, BlockEntityType<?> type, @Nullable CompoundTag nbt) {
            this.packedXZ = localXz;
            this.y = y;
            this.type = type;
            this.tag = nbt;
        }

        private BlockEntityInfo(RegistryFriendlyByteBuf buf) {
            this.packedXZ = buf.readByte();
            this.y = buf.readShort();
            this.type = ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).decode(buf);
            this.tag = buf.readNbt();
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(this.packedXZ);
            buf.writeShort(this.y);
            ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).encode(buf, this.type);
            buf.writeNbt(this.tag);
        }

        static ClientboundLevelChunkPacketData.BlockEntityInfo create(BlockEntity blockEntity) {
            CompoundTag compoundTag = blockEntity.getUpdateTag(blockEntity.getLevel().registryAccess());
            BlockPos blockPos = blockEntity.getBlockPos();
            int i = SectionPos.sectionRelative(blockPos.getX()) << 4 | SectionPos.sectionRelative(blockPos.getZ());
            return new ClientboundLevelChunkPacketData.BlockEntityInfo(i, blockPos.getY(), blockEntity.getType(), compoundTag.isEmpty() ? null : compoundTag);
        }
    }

    @FunctionalInterface
    public interface BlockEntityTagOutput {
        void accept(BlockPos pos, BlockEntityType<?> type, @Nullable CompoundTag nbt);
    }
}