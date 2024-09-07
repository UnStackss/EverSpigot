package net.minecraft.network.protocol.game;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public record CommonPlayerSpawnInfo(
    Holder<DimensionType> dimensionType,
    ResourceKey<Level> dimension,
    long seed,
    GameType gameType,
    @Nullable GameType previousGameType,
    boolean isDebug,
    boolean isFlat,
    Optional<GlobalPos> lastDeathLocation,
    int portalCooldown
) {
    public CommonPlayerSpawnInfo(RegistryFriendlyByteBuf buf) {
        this(
            DimensionType.STREAM_CODEC.decode(buf),
            buf.readResourceKey(Registries.DIMENSION),
            buf.readLong(),
            GameType.byId(buf.readByte()),
            GameType.byNullableId(buf.readByte()),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readOptional(FriendlyByteBuf::readGlobalPos),
            buf.readVarInt()
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        DimensionType.STREAM_CODEC.encode(buf, this.dimensionType);
        buf.writeResourceKey(this.dimension);
        buf.writeLong(this.seed);
        buf.writeByte(this.gameType.getId());
        buf.writeByte(GameType.getNullableId(this.previousGameType));
        buf.writeBoolean(this.isDebug);
        buf.writeBoolean(this.isFlat);
        buf.writeOptional(this.lastDeathLocation, FriendlyByteBuf::writeGlobalPos);
        buf.writeVarInt(this.portalCooldown);
    }
}
