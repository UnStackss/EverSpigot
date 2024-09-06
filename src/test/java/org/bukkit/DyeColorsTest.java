package org.bukkit;

import static org.bukkit.support.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import net.minecraft.world.item.DyeColor;
import org.bukkit.support.AbstractTestingBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class DyeColorsTest extends AbstractTestingBase {

    @ParameterizedTest
    @EnumSource(DyeColor.class)
    public void checkColor(DyeColor dye) {
        Color color = dye.getColor();
        int nmsColorArray = DyeColor.byId(dye.getWoolData()).getTextureDiffuseColor();
        Color nmsColor = Color.fromARGB(nmsColorArray);
        assertThat(color, is(nmsColor));
    }

    @ParameterizedTest
    @EnumSource(org.bukkit.DyeColor.class)
    public void checkFireworkColor(org.bukkit.DyeColor dye) {
        Color color = dye.getFireworkColor();
        int nmsColor = DyeColor.byId(dye.getWoolData()).getFireworkColor();
        assertThat(color, is(Color.fromRGB(nmsColor)));
    }
}
