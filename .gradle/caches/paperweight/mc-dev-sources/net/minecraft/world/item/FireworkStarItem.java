package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.FireworkExplosion;

public class FireworkStarItem extends Item {
    public FireworkStarItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        FireworkExplosion fireworkExplosion = stack.get(DataComponents.FIREWORK_EXPLOSION);
        if (fireworkExplosion != null) {
            fireworkExplosion.addToTooltip(context, tooltip::add, type);
        }
    }
}
