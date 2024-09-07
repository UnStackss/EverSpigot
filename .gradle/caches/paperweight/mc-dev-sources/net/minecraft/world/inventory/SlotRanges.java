package net.minecraft.world.inventory;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

public class SlotRanges {
    private static final List<SlotRange> SLOTS = Util.make(new ArrayList<>(), list -> {
        addSingleSlot(list, "contents", 0);
        addSlotRange(list, "container.", 0, 54);
        addSlotRange(list, "hotbar.", 0, 9);
        addSlotRange(list, "inventory.", 9, 27);
        addSlotRange(list, "enderchest.", 200, 27);
        addSlotRange(list, "villager.", 300, 8);
        addSlotRange(list, "horse.", 500, 15);
        int i = EquipmentSlot.MAINHAND.getIndex(98);
        int j = EquipmentSlot.OFFHAND.getIndex(98);
        addSingleSlot(list, "weapon", i);
        addSingleSlot(list, "weapon.mainhand", i);
        addSingleSlot(list, "weapon.offhand", j);
        addSlots(list, "weapon.*", i, j);
        i = EquipmentSlot.HEAD.getIndex(100);
        j = EquipmentSlot.CHEST.getIndex(100);
        int m = EquipmentSlot.LEGS.getIndex(100);
        int n = EquipmentSlot.FEET.getIndex(100);
        int o = EquipmentSlot.BODY.getIndex(105);
        addSingleSlot(list, "armor.head", i);
        addSingleSlot(list, "armor.chest", j);
        addSingleSlot(list, "armor.legs", m);
        addSingleSlot(list, "armor.feet", n);
        addSingleSlot(list, "armor.body", o);
        addSlots(list, "armor.*", i, j, m, n, o);
        addSingleSlot(list, "horse.saddle", 400);
        addSingleSlot(list, "horse.chest", 499);
        addSingleSlot(list, "player.cursor", 499);
        addSlotRange(list, "player.crafting.", 500, 4);
    });
    public static final Codec<SlotRange> CODEC = StringRepresentable.fromValues(() -> SLOTS.toArray(new SlotRange[0]));
    private static final Function<String, SlotRange> NAME_LOOKUP = StringRepresentable.createNameLookup(SLOTS.toArray(new SlotRange[0]), name -> name);

    private static SlotRange create(String name, int slotId) {
        return SlotRange.of(name, IntLists.singleton(slotId));
    }

    private static SlotRange create(String name, IntList slotIds) {
        return SlotRange.of(name, IntLists.unmodifiable(slotIds));
    }

    private static SlotRange create(String name, int... slotIds) {
        return SlotRange.of(name, IntList.of(slotIds));
    }

    private static void addSingleSlot(List<SlotRange> list, String name, int slotId) {
        list.add(create(name, slotId));
    }

    private static void addSlotRange(List<SlotRange> list, String baseName, int firstSlotId, int lastSlotId) {
        IntList intList = new IntArrayList(lastSlotId);

        for (int i = 0; i < lastSlotId; i++) {
            int j = firstSlotId + i;
            list.add(create(baseName + i, j));
            intList.add(j);
        }

        list.add(create(baseName + "*", intList));
    }

    private static void addSlots(List<SlotRange> list, String name, int... slots) {
        list.add(create(name, slots));
    }

    @Nullable
    public static SlotRange nameToIds(String name) {
        return NAME_LOOKUP.apply(name);
    }

    public static Stream<String> allNames() {
        return SLOTS.stream().map(StringRepresentable::getSerializedName);
    }

    public static Stream<String> singleSlotNames() {
        return SLOTS.stream().filter(slotRange -> slotRange.size() == 1).map(StringRepresentable::getSerializedName);
    }
}
