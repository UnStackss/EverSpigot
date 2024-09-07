package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimPattern;

public record ItemTrimPredicate(Optional<HolderSet<TrimMaterial>> material, Optional<HolderSet<TrimPattern>> pattern)
    implements SingleComponentItemPredicate<ArmorTrim> {
    public static final Codec<ItemTrimPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.TRIM_MATERIAL).optionalFieldOf("material").forGetter(ItemTrimPredicate::material),
                    RegistryCodecs.homogeneousList(Registries.TRIM_PATTERN).optionalFieldOf("pattern").forGetter(ItemTrimPredicate::pattern)
                )
                .apply(instance, ItemTrimPredicate::new)
    );

    @Override
    public DataComponentType<ArmorTrim> componentType() {
        return DataComponents.TRIM;
    }

    @Override
    public boolean matches(ItemStack stack, ArmorTrim component) {
        return (!this.material.isPresent() || this.material.get().contains(component.material()))
            && (!this.pattern.isPresent() || this.pattern.get().contains(component.pattern()));
    }
}
