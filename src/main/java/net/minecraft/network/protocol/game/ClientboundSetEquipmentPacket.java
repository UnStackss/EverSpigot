package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ClientboundSetEquipmentPacket implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetEquipmentPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEquipmentPacket::write, ClientboundSetEquipmentPacket::new
    );
    private static final byte CONTINUE_MASK = -128;
    private final int entity;
    private final List<Pair<EquipmentSlot, ItemStack>> slots;

    public ClientboundSetEquipmentPacket(int entityId, List<Pair<EquipmentSlot, ItemStack>> equipmentList) {
        // Paper start - data sanitization
        this(entityId, equipmentList, false);
    }
    private boolean sanitize;
    public ClientboundSetEquipmentPacket(int entityId, List<Pair<EquipmentSlot, ItemStack>> equipmentList, boolean sanitize) {
        this.sanitize = sanitize;
        // Paper end - data sanitization
        this.entity = entityId;
        this.slots = equipmentList;
    }

    private ClientboundSetEquipmentPacket(RegistryFriendlyByteBuf buf) {
        this.entity = buf.readVarInt();
        EquipmentSlot[] equipmentSlots = EquipmentSlot.values();
        this.slots = Lists.newArrayList();

        int i;
        do {
            i = buf.readByte();
            EquipmentSlot equipmentSlot = equipmentSlots[i & 127];
            ItemStack itemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            this.slots.add(Pair.of(equipmentSlot, itemStack));
        } while ((i & -128) != 0);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entity);
        int i = this.slots.size();

        try (var ignored = io.papermc.paper.util.DataSanitizationUtil.start(this.sanitize)) {  // Paper - data sanitization
        for (int j = 0; j < i; j++) {
            Pair<EquipmentSlot, ItemStack> pair = this.slots.get(j);
            EquipmentSlot equipmentSlot = pair.getFirst();
            boolean bl = j != i - 1;
            int k = equipmentSlot.ordinal();
            buf.writeByte(bl ? k | -128 : k);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pair.getSecond());
        }
        } // Paper - data sanitization
    }

    @Override
    public PacketType<ClientboundSetEquipmentPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_EQUIPMENT;
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetEquipment(this);
    }

    public int getEntity() {
        return this.entity;
    }

    public List<Pair<EquipmentSlot, ItemStack>> getSlots() {
        return this.slots;
    }
}
