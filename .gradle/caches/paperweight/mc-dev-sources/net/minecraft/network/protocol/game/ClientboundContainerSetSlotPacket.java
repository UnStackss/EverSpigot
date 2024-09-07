package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.item.ItemStack;

public class ClientboundContainerSetSlotPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundContainerSetSlotPacket> STREAM_CODEC = Packet.codec(
        ClientboundContainerSetSlotPacket::write, ClientboundContainerSetSlotPacket::new
    );
    public static final int CARRIED_ITEM = -1;
    public static final int PLAYER_INVENTORY = -2;
    private final int containerId;
    private final int stateId;
    private final int slot;
    private final ItemStack itemStack;

    public ClientboundContainerSetSlotPacket(int syncId, int revision, int slot, ItemStack stack) {
        this.containerId = syncId;
        this.stateId = revision;
        this.slot = slot;
        this.itemStack = stack.copy();
    }

    private ClientboundContainerSetSlotPacket(RegistryFriendlyByteBuf buf) {
        this.containerId = buf.readByte();
        this.stateId = buf.readVarInt();
        this.slot = buf.readShort();
        this.itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(this.containerId);
        buf.writeVarInt(this.stateId);
        buf.writeShort(this.slot);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.itemStack);
    }

    @Override
    public PacketType<ClientboundContainerSetSlotPacket> type() {
        return GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleContainerSetSlot(this);
    }

    public int getContainerId() {
        return this.containerId;
    }

    public int getSlot() {
        return this.slot;
    }

    public ItemStack getItem() {
        return this.itemStack;
    }

    public int getStateId() {
        return this.stateId;
    }
}
