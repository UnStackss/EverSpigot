package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;

public class SuspiciousStewItem extends Item {
    public static final int DEFAULT_DURATION = 160;

    public SuspiciousStewItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        super.appendHoverText(stack, context, tooltip, type);
        if (type.isCreative()) {
            List<MobEffectInstance> list = new ArrayList<>();
            SuspiciousStewEffects suspiciousStewEffects = stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);

            for (SuspiciousStewEffects.Entry entry : suspiciousStewEffects.effects()) {
                list.add(entry.createEffectInstance());
            }

            PotionContents.addPotionTooltip(list, tooltip::add, 1.0F, context.tickRate());
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        SuspiciousStewEffects suspiciousStewEffects = stack.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);

        for (SuspiciousStewEffects.Entry entry : suspiciousStewEffects.effects()) {
            user.addEffect(entry.createEffectInstance(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // Paper - Add missing effect cause
        }

        return super.finishUsingItem(stack, world, user);
    }
}
