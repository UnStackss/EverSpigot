package net.minecraft.world.item;

import net.minecraft.world.item.enchantment.EnchantmentInstance;

public class EnchantedBookItem extends Item {
    public EnchantedBookItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    public static ItemStack createForEnchantment(EnchantmentInstance info) {
        ItemStack itemStack = new ItemStack(Items.ENCHANTED_BOOK);
        itemStack.enchant(info.enchantment, info.level);
        return itemStack;
    }
}
