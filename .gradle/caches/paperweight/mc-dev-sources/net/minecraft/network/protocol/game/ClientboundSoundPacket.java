package net.minecraft.network.protocol.game;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ClientboundSoundPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSoundPacket> STREAM_CODEC = Packet.codec(
        ClientboundSoundPacket::write, ClientboundSoundPacket::new
    );
    public static final float LOCATION_ACCURACY = 8.0F;
    private final Holder<SoundEvent> sound;
    private final SoundSource source;
    private final int x;
    private final int y;
    private final int z;
    private final float volume;
    private final float pitch;
    private final long seed;

    public ClientboundSoundPacket(Holder<SoundEvent> sound, SoundSource category, double x, double y, double z, float volume, float pitch, long seed) {
        this.sound = sound;
        this.source = category;
        this.x = (int)(x * 8.0);
        this.y = (int)(y * 8.0);
        this.z = (int)(z * 8.0);
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    private ClientboundSoundPacket(RegistryFriendlyByteBuf buf) {
        this.sound = SoundEvent.STREAM_CODEC.decode(buf);
        this.source = buf.readEnum(SoundSource.class);
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.volume = buf.readFloat();
        this.pitch = buf.readFloat();
        this.seed = buf.readLong();
    }

    private void write(RegistryFriendlyByteBuf buf) {
        SoundEvent.STREAM_CODEC.encode(buf, this.sound);
        buf.writeEnum(this.source);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeFloat(this.volume);
        buf.writeFloat(this.pitch);
        buf.writeLong(this.seed);
    }

    @Override
    public PacketType<ClientboundSoundPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SOUND;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSoundEvent(this);
    }

    public Holder<SoundEvent> getSound() {
        return this.sound;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public double getX() {
        return (double)((float)this.x / 8.0F);
    }

    public double getY() {
        return (double)((float)this.y / 8.0F);
    }

    public double getZ() {
        return (double)((float)this.z / 8.0F);
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
