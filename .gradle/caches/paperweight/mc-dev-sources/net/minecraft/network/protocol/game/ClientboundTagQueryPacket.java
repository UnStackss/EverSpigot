package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundTagQueryPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTagQueryPacket> STREAM_CODEC = Packet.codec(
        ClientboundTagQueryPacket::write, ClientboundTagQueryPacket::new
    );
    private final int transactionId;
    @Nullable
    private final CompoundTag tag;

    public ClientboundTagQueryPacket(int transactionId, @Nullable CompoundTag nbt) {
        this.transactionId = transactionId;
        this.tag = nbt;
    }

    private ClientboundTagQueryPacket(FriendlyByteBuf buf) {
        this.transactionId = buf.readVarInt();
        this.tag = buf.readNbt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.transactionId);
        buf.writeNbt(this.tag);
    }

    @Override
    public PacketType<ClientboundTagQueryPacket> type() {
        return GamePacketTypes.CLIENTBOUND_TAG_QUERY;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleTagQueryPacket(this);
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    @Nullable
    public CompoundTag getTag() {
        return this.tag;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
