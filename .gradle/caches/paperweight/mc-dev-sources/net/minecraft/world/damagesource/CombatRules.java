package net.minecraft.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class CombatRules {
    public static final float MAX_ARMOR = 20.0F;
    public static final float ARMOR_PROTECTION_DIVIDER = 25.0F;
    public static final float BASE_ARMOR_TOUGHNESS = 2.0F;
    public static final float MIN_ARMOR_RATIO = 0.2F;
    private static final int NUM_ARMOR_ITEMS = 4;

    public static float getDamageAfterAbsorb(LivingEntity armorWearer, float damageAmount, DamageSource damageSource, float armor, float armorToughness) {
        float f = 2.0F + armorToughness / 4.0F;
        float g = Mth.clamp(armor - damageAmount / f, armor * 0.2F, 20.0F);
        float h = g / 25.0F;
        ItemStack itemStack = damageSource.getWeaponItem();
        float i;
        if (itemStack != null && armorWearer.level() instanceof ServerLevel serverLevel) {
            i = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(serverLevel, itemStack, armorWearer, damageSource, h), 0.0F, 1.0F);
        } else {
            i = h;
        }

        float k = 1.0F - i;
        return damageAmount * k;
    }

    public static float getDamageAfterMagicAbsorb(float damageDealt, float protection) {
        float f = Mth.clamp(protection, 0.0F, 20.0F);
        return damageDealt * (1.0F - f / 25.0F);
    }
}
