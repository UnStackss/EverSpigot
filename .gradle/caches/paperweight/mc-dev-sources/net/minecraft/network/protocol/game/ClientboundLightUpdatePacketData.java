package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ClientboundLightUpdatePacketData {
    private static final StreamCodec<ByteBuf, byte[]> DATA_LAYER_STREAM_CODEC = ByteBufCodecs.byteArray(2048);
    private final BitSet skyYMask;
    private final BitSet blockYMask;
    private final BitSet emptySkyYMask;
    private final BitSet emptyBlockYMask;
    private final List<byte[]> skyUpdates;
    private final List<byte[]> blockUpdates;

    public ClientboundLightUpdatePacketData(ChunkPos pos, LevelLightEngine lightProvider, @Nullable BitSet skyBits, @Nullable BitSet blockBits) {
        this.skyYMask = new BitSet();
        this.blockYMask = new BitSet();
        this.emptySkyYMask = new BitSet();
        this.emptyBlockYMask = new BitSet();
        this.skyUpdates = Lists.newArrayList();
        this.blockUpdates = Lists.newArrayList();

        for (int i = 0; i < lightProvider.getLightSectionCount(); i++) {
            if (skyBits == null || skyBits.get(i)) {
                this.prepareSectionData(pos, lightProvider, LightLayer.SKY, i, this.skyYMask, this.emptySkyYMask, this.skyUpdates);
            }

            if (blockBits == null || blockBits.get(i)) {
                this.prepareSectionData(pos, lightProvider, LightLayer.BLOCK, i, this.blockYMask, this.emptyBlockYMask, this.blockUpdates);
            }
        }
    }

    public ClientboundLightUpdatePacketData(FriendlyByteBuf buf, int x, int y) {
        this.skyYMask = buf.readBitSet();
        this.blockYMask = buf.readBitSet();
        this.emptySkyYMask = buf.readBitSet();
        this.emptyBlockYMask = buf.readBitSet();
        this.skyUpdates = buf.readList(DATA_LAYER_STREAM_CODEC);
        this.blockUpdates = buf.readList(DATA_LAYER_STREAM_CODEC);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBitSet(this.skyYMask);
        buf.writeBitSet(this.blockYMask);
        buf.writeBitSet(this.emptySkyYMask);
        buf.writeBitSet(this.emptyBlockYMask);
        buf.writeCollection(this.skyUpdates, DATA_LAYER_STREAM_CODEC);
        buf.writeCollection(this.blockUpdates, DATA_LAYER_STREAM_CODEC);
    }

    private void prepareSectionData(
        ChunkPos pos, LevelLightEngine lightProvider, LightLayer type, int y, BitSet initialized, BitSet uninitialized, List<byte[]> nibbles
    ) {
        DataLayer dataLayer = lightProvider.getLayerListener(type).getDataLayerData(SectionPos.of(pos, lightProvider.getMinLightSection() + y));
        if (dataLayer != null) {
            if (dataLayer.isEmpty()) {
                uninitialized.set(y);
            } else {
                initialized.set(y);
                nibbles.add(dataLayer.copy().getData());
            }
        }
    }

    public BitSet getSkyYMask() {
        return this.skyYMask;
    }

    public BitSet getEmptySkyYMask() {
        return this.emptySkyYMask;
    }

    public List<byte[]> getSkyUpdates() {
        return this.skyUpdates;
    }

    public BitSet getBlockYMask() {
        return this.blockYMask;
    }

    public BitSet getEmptyBlockYMask() {
        return this.emptyBlockYMask;
    }

    public List<byte[]> getBlockUpdates() {
        return this.blockUpdates;
    }
}
