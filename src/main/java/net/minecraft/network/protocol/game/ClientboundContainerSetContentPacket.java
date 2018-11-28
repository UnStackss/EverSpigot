package net.minecraft.network.protocol.game;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.item.ItemStack;

public class ClientboundContainerSetContentPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundContainerSetContentPacket> STREAM_CODEC = Packet.codec(
        ClientboundContainerSetContentPacket::write, ClientboundContainerSetContentPacket::new
    );
    private final int containerId;
    private final int stateId;
    private final List<ItemStack> items;
    private final ItemStack carriedItem;

    public ClientboundContainerSetContentPacket(int syncId, int revision, NonNullList<ItemStack> contents, ItemStack cursorStack) {
        this.containerId = syncId;
        this.stateId = revision;
        this.items = NonNullList.withSize(contents.size(), ItemStack.EMPTY);

        for (int i = 0; i < contents.size(); i++) {
            this.items.set(i, contents.get(i).copy());
        }

        this.carriedItem = cursorStack.copy();
    }

    private ClientboundContainerSetContentPacket(RegistryFriendlyByteBuf buf) {
        this.containerId = buf.readUnsignedByte();
        this.stateId = buf.readVarInt();
        this.items = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf);
        this.carriedItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
    }

    // Paper start - Handle large packets disconnecting client
    @Override
    public boolean hasLargePacketFallback() {
        return true;
    }

    @Override
    public boolean packetTooLarge(net.minecraft.network.Connection manager) {
        for (int i = 0 ; i < this.items.size() ; i++) {
            manager.send(new ClientboundContainerSetSlotPacket(this.containerId, this.stateId, i, this.items.get(i)));
        }
        return true;
    }
    // Paper end - Handle large packets disconnecting client

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeVarInt(this.stateId);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, this.items);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.carriedItem);
    }

    @Override
    public PacketType<ClientboundContainerSetContentPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleContainerContent(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public List<ItemStack> getItems() {
        return this.items;
    }

    public ItemStack getCarriedItem() {
        return this.carriedItem;
    }

    public int getStateId() {
        return this.stateId;
    }
}
