package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetExperiencePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetExperiencePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetExperiencePacket::write, ClientboundSetExperiencePacket::new
    );
    private final float experienceProgress;
    private final int totalExperience;
    private final int experienceLevel;

    public ClientboundSetExperiencePacket(float barProgress, int experienceLevel, int experience) {
        this.experienceProgress = barProgress;
        this.totalExperience = experienceLevel;
        this.experienceLevel = experience;
    }

    private ClientboundSetExperiencePacket(FriendlyByteBuf buf) {
        this.experienceProgress = buf.readFloat();
        this.experienceLevel = buf.readVarInt();
        this.totalExperience = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.experienceProgress);
        buf.writeVarInt(this.experienceLevel);
        buf.writeVarInt(this.totalExperience);
    }

    @Override
    public PacketType<ClientboundSetExperiencePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_EXPERIENCE;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetExperience(this);
    }

    public float getExperienceProgress() {
        return this.experienceProgress;
    }

    public int getTotalExperience() {
        return this.totalExperience;
    }

    public int getExperienceLevel() {
        return this.experienceLevel;
    }
}
