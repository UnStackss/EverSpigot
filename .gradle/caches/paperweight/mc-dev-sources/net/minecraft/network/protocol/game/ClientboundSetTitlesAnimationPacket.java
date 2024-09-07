package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetTitlesAnimationPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetTitlesAnimationPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetTitlesAnimationPacket::write, ClientboundSetTitlesAnimationPacket::new
    );
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public ClientboundSetTitlesAnimationPacket(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        this.fadeIn = fadeInTicks;
        this.stay = stayTicks;
        this.fadeOut = fadeOutTicks;
    }

    private ClientboundSetTitlesAnimationPacket(FriendlyByteBuf buf) {
        this.fadeIn = buf.readInt();
        this.stay = buf.readInt();
        this.fadeOut = buf.readInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.fadeIn);
        buf.writeInt(this.stay);
        buf.writeInt(this.fadeOut);
    }

    @Override
    public PacketType<ClientboundSetTitlesAnimationPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_TITLES_ANIMATION;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.setTitlesAnimation(this);
    }

    public int getFadeIn() {
        return this.fadeIn;
    }

    public int getStay() {
        return this.stay;
    }

    public int getFadeOut() {
        return this.fadeOut;
    }
}
