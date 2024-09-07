package net.minecraft.network.protocol.game;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record ClientboundLoginPacket(
    int playerId,
    boolean hardcore,
    Set<ResourceKey<Level>> levels,
    int maxPlayers,
    int chunkRadius,
    int simulationDistance,
    boolean reducedDebugInfo,
    boolean showDeathScreen,
    boolean doLimitedCrafting,
    CommonPlayerSpawnInfo commonPlayerSpawnInfo,
    boolean enforcesSecureChat
) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundLoginPacket> STREAM_CODEC = Packet.codec(
        ClientboundLoginPacket::write, ClientboundLoginPacket::new
    );

    private ClientboundLoginPacket(RegistryFriendlyByteBuf buf) {
        this(
            buf.readInt(),
            buf.readBoolean(),
            buf.readCollection(Sets::newHashSetWithExpectedSize, b -> b.readResourceKey(Registries.DIMENSION)),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            new CommonPlayerSpawnInfo(buf),
            buf.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.playerId);
        buf.writeBoolean(this.hardcore);
        buf.writeCollection(this.levels, FriendlyByteBuf::writeResourceKey);
        buf.writeVarInt(this.maxPlayers);
        buf.writeVarInt(this.chunkRadius);
        buf.writeVarInt(this.simulationDistance);
        buf.writeBoolean(this.reducedDebugInfo);
        buf.writeBoolean(this.showDeathScreen);
        buf.writeBoolean(this.doLimitedCrafting);
        this.commonPlayerSpawnInfo.write(buf);
        buf.writeBoolean(this.enforcesSecureChat);
    }

    @Override
    public PacketType<ClientboundLoginPacket> type() {
        return GamePacketTypes.CLIENTBOUND_LOGIN;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleLogin(this);
    }
}
