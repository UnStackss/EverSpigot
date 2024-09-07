package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class ClientboundSoundEntityPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSoundEntityPacket> STREAM_CODEC = Packet.codec(
        ClientboundSoundEntityPacket::write, ClientboundSoundEntityPacket::new
    );
    private final Holder<SoundEvent> sound;
    private final SoundSource source;
    private final int id;
    private final float volume;
    private final float pitch;
    private final long seed;

    public ClientboundSoundEntityPacket(Holder<SoundEvent> sound, SoundSource category, Entity entity, float volume, float pitch, long seed) {
        this.sound = sound;
        this.source = category;
        this.id = entity.getId();
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    private ClientboundSoundEntityPacket(RegistryFriendlyByteBuf buf) {
        this.sound = SoundEvent.STREAM_CODEC.decode(buf);
        this.source = buf.readEnum(SoundSource.class);
        this.id = buf.readVarInt();
        this.volume = buf.readFloat();
        this.pitch = buf.readFloat();
        this.seed = buf.readLong();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        SoundEvent.STREAM_CODEC.encode(buf, this.sound);
        buf.writeEnum(this.source);
        buf.writeVarInt(this.id);
        buf.writeFloat(this.volume);
        buf.writeFloat(this.pitch);
        buf.writeLong(this.seed);
    }

    @Override
    public PacketType<ClientboundSoundEntityPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SOUND_ENTITY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSoundEntityEvent(this);
    }

    public Holder<SoundEvent> getSound() {
        return this.sound;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public int getId() {
        return this.id;
    }

    public float getVolume() {
        return this.volume;
    }

    public float getPitch() {
        return this.pitch;
    }

    public long getSeed() {
        return this.seed;
    }
}
