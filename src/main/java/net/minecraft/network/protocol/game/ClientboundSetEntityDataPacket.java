package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.syncher.SynchedEntityData;

public record ClientboundSetEntityDataPacket(int id, List<SynchedEntityData.DataValue<?>> packedItems) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetEntityDataPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityDataPacket::write, ClientboundSetEntityDataPacket::new
    );
    public static final int EOF_MARKER = 255;

    private ClientboundSetEntityDataPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), unpack(buf));
    }

    private static void pack(List<SynchedEntityData.DataValue<?>> trackedValues, RegistryFriendlyByteBuf buf) {
        try (var ignored = io.papermc.paper.util.DataSanitizationUtil.start(true)) { // Paper - data sanitization
        for (SynchedEntityData.DataValue<?> dataValue : trackedValues) {
            dataValue.write(buf);
        }
        } // Paper - data sanitization

        buf.writeByte(255);
    }

    private static List<SynchedEntityData.DataValue<?>> unpack(RegistryFriendlyByteBuf buf) {
        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();

        int i;
        while ((i = buf.readUnsignedByte()) != 255) {
            list.add(SynchedEntityData.DataValue.read(buf, i));
        }

        return list;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.id);
        pack(this.packedItems, buf);
    }

    @Override
    public PacketType<ClientboundSetEntityDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetEntityData(this);
    }
}
