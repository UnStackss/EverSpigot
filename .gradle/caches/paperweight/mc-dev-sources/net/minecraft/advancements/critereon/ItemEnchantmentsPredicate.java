package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public abstract class ItemEnchantmentsPredicate implements SingleComponentItemPredicate<ItemEnchantments> {
    private final List<EnchantmentPredicate> enchantments;

    protected ItemEnchantmentsPredicate(List<EnchantmentPredicate> enchantments) {
        this.enchantments = enchantments;
    }

    public static <T extends ItemEnchantmentsPredicate> Codec<T> codec(Function<List<EnchantmentPredicate>, T> predicateFunction) {
        return EnchantmentPredicate.CODEC.listOf().xmap(predicateFunction, ItemEnchantmentsPredicate::enchantments);
    }

    protected List<EnchantmentPredicate> enchantments() {
        return this.enchantments;
    }

    @Override
    public boolean matches(ItemStack stack, ItemEnchantments component) {
        for (EnchantmentPredicate enchantmentPredicate : this.enchantments) {
            if (!enchantmentPredicate.containedIn(component)) {
                return false;
            }
        }

        return true;
    }

    public static ItemEnchantmentsPredicate.Enchantments enchantments(List<EnchantmentPredicate> enchantments) {
        return new ItemEnchantmentsPredicate.Enchantments(enchantments);
    }

    public static ItemEnchantmentsPredicate.StoredEnchantments storedEnchantments(List<EnchantmentPredicate> storedEnchantments) {
        return new ItemEnchantmentsPredicate.StoredEnchantments(storedEnchantments);
    }

    public static class Enchantments extends ItemEnchantmentsPredicate {
        public static final Codec<ItemEnchantmentsPredicate.Enchantments> CODEC = codec(ItemEnchantmentsPredicate.Enchantments::new);

        protected Enchantments(List<EnchantmentPredicate> enchantments) {
            super(enchantments);
        }

        @Override
        public DataComponentType<ItemEnchantments> componentType() {
            return DataComponents.ENCHANTMENTS;
        }
    }

    public static class StoredEnchantments extends ItemEnchantmentsPredicate {
        public static final Codec<ItemEnchantmentsPredicate.StoredEnchantments> CODEC = codec(ItemEnchantmentsPredicate.StoredEnchantments::new);

        protected StoredEnchantments(List<EnchantmentPredicate> enchantments) {
            super(enchantments);
        }

        @Override
        public DataComponentType<ItemEnchantments> componentType() {
            return DataComponents.STORED_ENCHANTMENTS;
        }
    }
}
