package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.damagesource.CombatTracker;

public class ClientboundPlayerCombatEndPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundPlayerCombatEndPacket> STREAM_CODEC = Packet.codec(
        ClientboundPlayerCombatEndPacket::write, ClientboundPlayerCombatEndPacket::new
    );
    private final int duration;

    public ClientboundPlayerCombatEndPacket(CombatTracker damageTracker) {
        this(damageTracker.getCombatDuration());
    }

    public ClientboundPlayerCombatEndPacket(int timeSinceLastAttack) {
        this.duration = timeSinceLastAttack;
    }

    private ClientboundPlayerCombatEndPacket(FriendlyByteBuf buf) {
        this.duration = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.duration);
    }

    @Override
    public PacketType<ClientboundPlayerCombatEndPacket> type() {
        return GamePacketTypes.CLIENTBOUND_PLAYER_COMBAT_END;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handlePlayerCombatEnd(this);
    }
}
