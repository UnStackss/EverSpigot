package net.minecraft.world.entity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum EquipmentSlotGroup implements StringRepresentable {
    ANY(0, "any", slot -> true),
    MAINHAND(1, "mainhand", EquipmentSlot.MAINHAND),
    OFFHAND(2, "offhand", EquipmentSlot.OFFHAND),
    HAND(3, "hand", slot -> slot.getType() == EquipmentSlot.Type.HAND),
    FEET(4, "feet", EquipmentSlot.FEET),
    LEGS(5, "legs", EquipmentSlot.LEGS),
    CHEST(6, "chest", EquipmentSlot.CHEST),
    HEAD(7, "head", EquipmentSlot.HEAD),
    ARMOR(8, "armor", EquipmentSlot::isArmor),
    BODY(9, "body", EquipmentSlot.BODY);

    public static final IntFunction<EquipmentSlotGroup> BY_ID = ByIdMap.continuous(id -> id.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final Codec<EquipmentSlotGroup> CODEC = StringRepresentable.fromEnum(EquipmentSlotGroup::values);
    public static final StreamCodec<ByteBuf, EquipmentSlotGroup> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, id -> id.id);
    private final int id;
    private final String key;
    private final Predicate<EquipmentSlot> predicate;

    private EquipmentSlotGroup(final int id, final String name, final Predicate<EquipmentSlot> slotPredicate) {
        this.id = id;
        this.key = name;
        this.predicate = slotPredicate;
    }

    private EquipmentSlotGroup(final int id, final String name, final EquipmentSlot slot) {
        this(id, name, slotx -> slotx == slot);
    }

    public static EquipmentSlotGroup bySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> MAINHAND;
            case OFFHAND -> OFFHAND;
            case FEET -> FEET;
            case LEGS -> LEGS;
            case CHEST -> CHEST;
            case HEAD -> HEAD;
            case BODY -> BODY;
        };
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    public boolean test(EquipmentSlot slot) {
        return this.predicate.test(slot);
    }
}
