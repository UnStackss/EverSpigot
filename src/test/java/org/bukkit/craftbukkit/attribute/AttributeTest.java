package org.bukkit.craftbukkit.attribute;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import org.bukkit.attribute.Attribute;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.api.Test;

public class AttributeTest extends AbstractTestingBase {

    @Test
    public void testToBukkit() {
        for (net.minecraft.world.entity.ai.attributes.Attribute nms : BuiltInRegistries.ATTRIBUTE) {
            Attribute bukkit = CraftAttribute.minecraftToBukkit(nms);

            assertNotNull(bukkit, nms.toString());
        }
    }

    @Test
    public void testToNMS() {
        for (Attribute attribute : Attribute.values()) {
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> nms = CraftAttribute.bukkitToMinecraftHolder(attribute);

            assertNotNull(nms, attribute.name());
        }
    }
}
