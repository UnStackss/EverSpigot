package net.minecraft.world.item.crafting;

import com.mojang.datafixers.util.Pair;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

public class RepairItemRecipe extends CustomRecipe {
    public RepairItemRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Nullable
    private Pair<ItemStack, ItemStack> getItemsToCombine(CraftingInput input) {
        ItemStack itemStack = null;
        ItemStack itemStack2 = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack3 = input.getItem(i);
            if (!itemStack3.isEmpty()) {
                if (itemStack == null) {
                    itemStack = itemStack3;
                } else {
                    if (itemStack2 != null) {
                        return null;
                    }

                    itemStack2 = itemStack3;
                }
            }
        }

        return itemStack != null && itemStack2 != null && canCombine(itemStack, itemStack2) ? Pair.of(itemStack, itemStack2) : null;
    }

    private static boolean canCombine(ItemStack first, ItemStack second) {
        return second.is(first.getItem())
            && first.getCount() == 1
            && second.getCount() == 1
            && first.has(DataComponents.MAX_DAMAGE)
            && second.has(DataComponents.MAX_DAMAGE)
            && first.has(DataComponents.DAMAGE)
            && second.has(DataComponents.DAMAGE);
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        return this.getItemsToCombine(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        Pair<ItemStack, ItemStack> pair = this.getItemsToCombine(input);
        if (pair == null) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemStack = pair.getFirst();
            ItemStack itemStack2 = pair.getSecond();
            int i = Math.max(itemStack.getMaxDamage(), itemStack2.getMaxDamage());
            int j = itemStack.getMaxDamage() - itemStack.getDamageValue();
            int k = itemStack2.getMaxDamage() - itemStack2.getDamageValue();
            int l = j + k + i * 5 / 100;
            ItemStack itemStack3 = new ItemStack(itemStack.getItem());
            itemStack3.set(DataComponents.MAX_DAMAGE, i);
            itemStack3.setDamageValue(Math.max(i - l, 0));
            ItemEnchantments itemEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemStack);
            ItemEnchantments itemEnchantments2 = EnchantmentHelper.getEnchantmentsForCrafting(itemStack2);
            EnchantmentHelper.updateEnchantments(
                itemStack3,
                builder -> lookup.lookupOrThrow(Registries.ENCHANTMENT)
                        .listElements()
                        .filter(enchantment -> enchantment.is(EnchantmentTags.CURSE))
                        .forEach(enchantment -> {
                            int ix = Math.max(itemEnchantments.getLevel(enchantment), itemEnchantments2.getLevel(enchantment));
                            if (ix > 0) {
                                builder.upgrade(enchantment, ix);
                            }
                        })
            );
            return itemStack3;
        }
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.REPAIR_ITEM;
    }
}
