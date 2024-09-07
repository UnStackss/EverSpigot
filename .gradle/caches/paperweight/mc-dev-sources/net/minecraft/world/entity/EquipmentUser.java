package net.minecraft.world.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

public interface EquipmentUser {
    void setItemSlot(EquipmentSlot slot, ItemStack stack);

    ItemStack getItemBySlot(EquipmentSlot slot);

    void setDropChance(EquipmentSlot slot, float dropChance);

    default void equip(EquipmentTable equipmentTable, LootParams parameters) {
        this.equip(equipmentTable.lootTable(), parameters, equipmentTable.slotDropChances());
    }

    default void equip(ResourceKey<LootTable> lootTable, LootParams parameters, Map<EquipmentSlot, Float> slotDropChances) {
        this.equip(lootTable, parameters, 0L, slotDropChances);
    }

    default void equip(ResourceKey<LootTable> lootTable, LootParams parameters, long seed, Map<EquipmentSlot, Float> slotDropChances) {
        if (!lootTable.equals(BuiltInLootTables.EMPTY)) {
            LootTable lootTable2 = parameters.getLevel().getServer().reloadableRegistries().getLootTable(lootTable);
            if (lootTable2 != LootTable.EMPTY) {
                List<ItemStack> list = lootTable2.getRandomItems(parameters, seed);
                List<EquipmentSlot> list2 = new ArrayList<>();

                for (ItemStack itemStack : list) {
                    EquipmentSlot equipmentSlot = this.resolveSlot(itemStack, list2);
                    if (equipmentSlot != null) {
                        ItemStack itemStack2 = equipmentSlot.limit(itemStack);
                        this.setItemSlot(equipmentSlot, itemStack2);
                        Float float_ = slotDropChances.get(equipmentSlot);
                        if (float_ != null) {
                            this.setDropChance(equipmentSlot, float_);
                        }

                        list2.add(equipmentSlot);
                    }
                }
            }
        }
    }

    @Nullable
    default EquipmentSlot resolveSlot(ItemStack stack, List<EquipmentSlot> slotBlacklist) {
        if (stack.isEmpty()) {
            return null;
        } else {
            Equipable equipable = Equipable.get(stack);
            if (equipable != null) {
                EquipmentSlot equipmentSlot = equipable.getEquipmentSlot();
                if (!slotBlacklist.contains(equipmentSlot)) {
                    return equipmentSlot;
                }
            } else if (!slotBlacklist.contains(EquipmentSlot.MAINHAND)) {
                return EquipmentSlot.MAINHAND;
            }

            return null;
        }
    }
}
