package net.minecraft.world.item.enchantment.providers;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record EnchantmentsByCost(HolderSet<Enchantment> enchantments, IntProvider cost) implements EnchantmentProvider {
    public static final MapCodec<EnchantmentsByCost> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).fieldOf("enchantments").forGetter(EnchantmentsByCost::enchantments),
                    IntProvider.CODEC.fieldOf("cost").forGetter(EnchantmentsByCost::cost)
                )
                .apply(instance, EnchantmentsByCost::new)
    );

    @Override
    public void enchant(ItemStack stack, ItemEnchantments.Mutable componentBuilder, RandomSource random, DifficultyInstance localDifficulty) {
        for (EnchantmentInstance enchantmentInstance : EnchantmentHelper.selectEnchantment(random, stack, this.cost.sample(random), this.enchantments.stream())) {
            componentBuilder.upgrade(enchantmentInstance.enchantment, enchantmentInstance.level);
        }
    }

    @Override
    public MapCodec<EnchantmentsByCost> codec() {
        return CODEC;
    }
}
