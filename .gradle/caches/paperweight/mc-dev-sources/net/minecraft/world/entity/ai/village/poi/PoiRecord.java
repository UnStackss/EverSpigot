package net.minecraft.world.entity.ai.village.poi;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.VisibleForDebug;

public class PoiRecord {
    private final BlockPos pos;
    private final Holder<PoiType> poiType;
    private int freeTickets;
    private final Runnable setDirty;

    public static Codec<PoiRecord> codec(Runnable updateListener) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(poi -> poi.pos),
                        RegistryFixedCodec.create(Registries.POINT_OF_INTEREST_TYPE).fieldOf("type").forGetter(poi -> poi.poiType),
                        Codec.INT.fieldOf("free_tickets").orElse(0).forGetter(poi -> poi.freeTickets),
                        RecordCodecBuilder.point(updateListener)
                    )
                    .apply(instance, PoiRecord::new)
        );
    }

    private PoiRecord(BlockPos pos, Holder<PoiType> type, int freeTickets, Runnable updateListener) {
        this.pos = pos.immutable();
        this.poiType = type;
        this.freeTickets = freeTickets;
        this.setDirty = updateListener;
    }

    public PoiRecord(BlockPos pos, Holder<PoiType> type, Runnable updateListener) {
        this(pos, type, type.value().maxTickets(), updateListener);
    }

    @Deprecated
    @VisibleForDebug
    public int getFreeTickets() {
        return this.freeTickets;
    }

    protected boolean acquireTicket() {
        if (this.freeTickets <= 0) {
            return false;
        } else {
            this.freeTickets--;
            this.setDirty.run();
            return true;
        }
    }

    protected boolean releaseTicket() {
        if (this.freeTickets >= this.poiType.value().maxTickets()) {
            return false;
        } else {
            this.freeTickets++;
            this.setDirty.run();
            return true;
        }
    }

    public boolean hasSpace() {
        return this.freeTickets > 0;
    }

    public boolean isOccupied() {
        return this.freeTickets != this.poiType.value().maxTickets();
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Holder<PoiType> getPoiType() {
        return this.poiType;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object != null && this.getClass() == object.getClass() && Objects.equals(this.pos, ((PoiRecord)object).pos);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode();
    }
}
