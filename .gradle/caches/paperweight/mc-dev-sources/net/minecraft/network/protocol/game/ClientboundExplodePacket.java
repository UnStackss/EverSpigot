package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

public class ClientboundExplodePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundExplodePacket> STREAM_CODEC = Packet.codec(
        ClientboundExplodePacket::write, ClientboundExplodePacket::new
    );
    private final double x;
    private final double y;
    private final double z;
    private final float power;
    private final List<BlockPos> toBlow;
    private final float knockbackX;
    private final float knockbackY;
    private final float knockbackZ;
    private final ParticleOptions smallExplosionParticles;
    private final ParticleOptions largeExplosionParticles;
    private final Explosion.BlockInteraction blockInteraction;
    private final Holder<SoundEvent> explosionSound;

    public ClientboundExplodePacket(
        double x,
        double y,
        double z,
        float radius,
        List<BlockPos> affectedBlocks,
        @Nullable Vec3 playerVelocity,
        Explosion.BlockInteraction destructionType,
        ParticleOptions particle,
        ParticleOptions emitterParticle,
        Holder<SoundEvent> holder
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = radius;
        this.toBlow = Lists.newArrayList(affectedBlocks);
        this.explosionSound = holder;
        if (playerVelocity != null) {
            this.knockbackX = (float)playerVelocity.x;
            this.knockbackY = (float)playerVelocity.y;
            this.knockbackZ = (float)playerVelocity.z;
        } else {
            this.knockbackX = 0.0F;
            this.knockbackY = 0.0F;
            this.knockbackZ = 0.0F;
        }

        this.blockInteraction = destructionType;
        this.smallExplosionParticles = particle;
        this.largeExplosionParticles = emitterParticle;
    }

    private ClientboundExplodePacket(RegistryFriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.power = buf.readFloat();
        int i = Mth.floor(this.x);
        int j = Mth.floor(this.y);
        int k = Mth.floor(this.z);
        this.toBlow = buf.readList(buf2 -> {
            int l = buf2.readByte() + i;
            int m = buf2.readByte() + j;
            int n = buf2.readByte() + k;
            return new BlockPos(l, m, n);
        });
        this.knockbackX = buf.readFloat();
        this.knockbackY = buf.readFloat();
        this.knockbackZ = buf.readFloat();
        this.blockInteraction = buf.readEnum(Explosion.BlockInteraction.class);
        this.smallExplosionParticles = ParticleTypes.STREAM_CODEC.decode(buf);
        this.largeExplosionParticles = ParticleTypes.STREAM_CODEC.decode(buf);
        this.explosionSound = SoundEvent.STREAM_CODEC.decode(buf);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.power);
        int i = Mth.floor(this.x);
        int j = Mth.floor(this.y);
        int k = Mth.floor(this.z);
        buf.writeCollection(this.toBlow, (buf2, pos) -> {
            int l = pos.getX() - i;
            int m = pos.getY() - j;
            int n = pos.getZ() - k;
            buf2.writeByte(l);
            buf2.writeByte(m);
            buf2.writeByte(n);
        });
        buf.writeFloat(this.knockbackX);
        buf.writeFloat(this.knockbackY);
        buf.writeFloat(this.knockbackZ);
        buf.writeEnum(this.blockInteraction);
        ParticleTypes.STREAM_CODEC.encode(buf, this.smallExplosionParticles);
        ParticleTypes.STREAM_CODEC.encode(buf, this.largeExplosionParticles);
        SoundEvent.STREAM_CODEC.encode(buf, this.explosionSound);
    }

    @Override
    public PacketType<ClientboundExplodePacket> type() {
        return GamePacketTypes.CLIENTBOUND_EXPLODE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleExplosion(this);
    }

    public float getKnockbackX() {
        return this.knockbackX;
    }

    public float getKnockbackY() {
        return this.knockbackY;
    }

    public float getKnockbackZ() {
        return this.knockbackZ;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getPower() {
        return this.power;
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public Explosion.BlockInteraction getBlockInteraction() {
        return this.blockInteraction;
    }

    public ParticleOptions getSmallExplosionParticles() {
        return this.smallExplosionParticles;
    }

    public ParticleOptions getLargeExplosionParticles() {
        return this.largeExplosionParticles;
    }

    public Holder<SoundEvent> getExplosionSound() {
        return this.explosionSound;
    }
}
