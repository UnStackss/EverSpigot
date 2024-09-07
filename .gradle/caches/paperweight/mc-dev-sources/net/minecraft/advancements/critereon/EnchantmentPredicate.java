package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record EnchantmentPredicate(Optional<HolderSet<Enchantment>> enchantments, MinMaxBounds.Ints level) {
    public static final Codec<EnchantmentPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("enchantments").forGetter(EnchantmentPredicate::enchantments),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("levels", MinMaxBounds.Ints.ANY).forGetter(EnchantmentPredicate::level)
                )
                .apply(instance, EnchantmentPredicate::new)
    );

    public EnchantmentPredicate(Holder<Enchantment> enchantment, MinMaxBounds.Ints levels) {
        this(Optional.of(HolderSet.direct(enchantment)), levels);
    }

    public EnchantmentPredicate(HolderSet<Enchantment> enchantments, MinMaxBounds.Ints levels) {
        this(Optional.of(enchantments), levels);
    }

    public boolean containedIn(ItemEnchantments enchantmentsComponent) {
        if (this.enchantments.isPresent()) {
            for (Holder<Enchantment> holder : this.enchantments.get()) {
                if (this.matchesEnchantment(enchantmentsComponent, holder)) {
                    return true;
                }
            }

            return false;
        } else if (this.level != MinMaxBounds.Ints.ANY) {
            for (Entry<Holder<Enchantment>> entry : enchantmentsComponent.entrySet()) {
                if (this.level.matches(entry.getIntValue())) {
                    return true;
                }
            }

            return false;
        } else {
            return !enchantmentsComponent.isEmpty();
        }
    }

    private boolean matchesEnchantment(ItemEnchantments enchantmentsComponent, Holder<Enchantment> enchantment) {
        int i = enchantmentsComponent.getLevel(enchantment);
        return i != 0 && (this.level == MinMaxBounds.Ints.ANY || this.level.matches(i));
    }
}
