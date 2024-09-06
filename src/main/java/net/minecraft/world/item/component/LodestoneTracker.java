package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

public record LodestoneTracker(Optional<GlobalPos> target, boolean tracked) {
    public static final Codec<LodestoneTracker> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    GlobalPos.CODEC.optionalFieldOf("target").forGetter(LodestoneTracker::target),
                    Codec.BOOL.optionalFieldOf("tracked", Boolean.valueOf(true)).forGetter(LodestoneTracker::tracked)
                )
                .apply(instance, LodestoneTracker::new)
    );
    public static final StreamCodec<ByteBuf, LodestoneTracker> STREAM_CODEC = StreamCodec.composite(
        GlobalPos.STREAM_CODEC.apply(ByteBufCodecs::optional), LodestoneTracker::target, ByteBufCodecs.BOOL, LodestoneTracker::tracked, LodestoneTracker::new
    );

    public LodestoneTracker tick(ServerLevel world) {
        if (this.tracked && !this.target.isEmpty()) {
            if (this.target.get().dimension() != world.dimension()) {
                return this;
            } else {
                BlockPos blockPos = this.target.get().pos();
                return world.isInWorldBounds(blockPos) && (!world.hasChunkAt(blockPos) || world.getPoiManager().existsAtPosition(PoiTypes.LODESTONE, blockPos)) // Paper - Prevent compass from loading chunks
                    ? this
                    : new LodestoneTracker(Optional.empty(), true);
            }
        } else {
            return this;
        }
    }
}
