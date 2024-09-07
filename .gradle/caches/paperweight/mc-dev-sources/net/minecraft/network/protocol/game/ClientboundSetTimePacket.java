package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundSetTimePacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundSetTimePacket> STREAM_CODEC = Packet.codec(
        ClientboundSetTimePacket::write, ClientboundSetTimePacket::new
    );
    private final long gameTime;
    private final long dayTime;

    public ClientboundSetTimePacket(long time, long timeOfDay, boolean doDaylightCycle) {
        this.gameTime = time;
        long l = timeOfDay;
        if (!doDaylightCycle) {
            l = -timeOfDay;
            if (l == 0L) {
                l = -1L;
            }
        }

        this.dayTime = l;
    }

    private ClientboundSetTimePacket(FriendlyByteBuf buf) {
        this.gameTime = buf.readLong();
        this.dayTime = buf.readLong();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeLong(this.gameTime);
        buf.writeLong(this.dayTime);
    }

    @Override
    public PacketType<ClientboundSetTimePacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_TIME;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetTime(this);
    }

    public long getGameTime() {
        return this.gameTime;
    }

    public long getDayTime() {
        return this.dayTime;
    }
}
