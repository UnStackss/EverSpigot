package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundOpenSignEditorPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundOpenSignEditorPacket> STREAM_CODEC = Packet.codec(
        ClientboundOpenSignEditorPacket::write, ClientboundOpenSignEditorPacket::new
    );
    private final BlockPos pos;
    private final boolean isFrontText;

    public ClientboundOpenSignEditorPacket(BlockPos pos, boolean front) {
        this.pos = pos;
        this.isFrontText = front;
    }

    private ClientboundOpenSignEditorPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.isFrontText = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isFrontText);
    }

    @Override
    public PacketType<ClientboundOpenSignEditorPacket> type() {
        return GamePacketTypes.CLIENTBOUND_OPEN_SIGN_EDITOR;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleOpenSignEditor(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public boolean isFrontText() {
        return this.isFrontText;
    }
}
