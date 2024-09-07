package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class ClientboundUpdateMobEffectPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundUpdateMobEffectPacket> STREAM_CODEC = Packet.codec(
        ClientboundUpdateMobEffectPacket::write, ClientboundUpdateMobEffectPacket::new
    );
    private static final int FLAG_AMBIENT = 1;
    private static final int FLAG_VISIBLE = 2;
    private static final int FLAG_SHOW_ICON = 4;
    private static final int FLAG_BLEND = 8;
    private final int entityId;
    private final Holder<MobEffect> effect;
    private final int effectAmplifier;
    private final int effectDurationTicks;
    private final byte flags;

    public ClientboundUpdateMobEffectPacket(int entityId, MobEffectInstance effect, boolean keepFading) {
        this.entityId = entityId;
        this.effect = effect.getEffect();
        this.effectAmplifier = effect.getAmplifier();
        this.effectDurationTicks = effect.getDuration();
        byte b = 0;
        if (effect.isAmbient()) {
            b = (byte)(b | 1);
        }

        if (effect.isVisible()) {
            b = (byte)(b | 2);
        }

        if (effect.showIcon()) {
            b = (byte)(b | 4);
        }

        if (keepFading) {
            b = (byte)(b | 8);
        }

        this.flags = b;
    }

    private ClientboundUpdateMobEffectPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.effect = MobEffect.STREAM_CODEC.decode(buf);
        this.effectAmplifier = buf.readVarInt();
        this.effectDurationTicks = buf.readVarInt();
        this.flags = buf.readByte();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        MobEffect.STREAM_CODEC.encode(buf, this.effect);
        buf.writeVarInt(this.effectAmplifier);
        buf.writeVarInt(this.effectDurationTicks);
        buf.writeByte(this.flags);
    }

    @Override
    public PacketType<ClientboundUpdateMobEffectPacket> type() {
        return GamePacketTypes.CLIENTBOUND_UPDATE_MOB_EFFECT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleUpdateMobEffect(this);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getEffectAmplifier() {
        return this.effectAmplifier;
    }

    public int getEffectDurationTicks() {
        return this.effectDurationTicks;
    }

    public boolean isEffectVisible() {
        return (this.flags & 2) != 0;
    }

    public boolean isEffectAmbient() {
        return (this.flags & 1) != 0;
    }

    public boolean effectShowsIcon() {
        return (this.flags & 4) != 0;
    }

    public boolean shouldBlend() {
        return (this.flags & 8) != 0;
    }
}
