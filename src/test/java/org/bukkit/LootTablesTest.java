package org.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.resources.ResourceKey;
import org.bukkit.craftbukkit.CraftLootTable;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

public class LootTablesTest extends AbstractTestingBase {

    @Test
    public void testLootTablesEnumExists() {
        LootTables[] tables = LootTables.values();

        for (LootTables table : tables) {
            LootTable lootTable = Bukkit.getLootTable(table.getKey());

            assertNotNull(lootTable, "Unknown LootTable " + table.getKey());
            assertEquals(lootTable.getKey(), table.getKey());
        }
    }

    @Test
    public void testNMS() {
        for (ResourceKey<net.minecraft.world.level.storage.loot.LootTable> key : net.minecraft.world.level.storage.loot.BuiltInLootTables.all()) {
            NamespacedKey bukkitKey = CraftLootTable.minecraftToBukkitKey(key);
            LootTables lootTable = Registry.LOOT_TABLES.get(bukkitKey);

            assertNotNull(lootTable, "Unknown LootTable " + key);
            assertEquals(lootTable.getKey(), bukkitKey);
        }
    }
}
