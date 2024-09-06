package org.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

public class EnchantmentTest extends AbstractTestingBase {

    @Test
    public void verifyMapping() {
        for (ResourceLocation key : CraftRegistry.getMinecraftRegistry(Registries.ENCHANTMENT).keySet()) {
            net.minecraft.world.item.enchantment.Enchantment nms = CraftRegistry.getMinecraftRegistry(Registries.ENCHANTMENT).get(key);

            Enchantment bukkitById = Enchantment.getByKey(CraftNamespacedKey.fromMinecraft(key));

            assertFalse(bukkitById.getName().startsWith("UNKNOWN"), "Unknown enchant name for " + key);
        }
    }
}
