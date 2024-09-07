package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

public record ItemBundlePredicate(Optional<CollectionPredicate<ItemStack, ItemPredicate>> items) implements SingleComponentItemPredicate<BundleContents> {
    public static final Codec<ItemBundlePredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    CollectionPredicate.<ItemStack, ItemPredicate>codec(ItemPredicate.CODEC).optionalFieldOf("items").forGetter(ItemBundlePredicate::items)
                )
                .apply(instance, ItemBundlePredicate::new)
    );

    @Override
    public DataComponentType<BundleContents> componentType() {
        return DataComponents.BUNDLE_CONTENTS;
    }

    @Override
    public boolean matches(ItemStack stack, BundleContents component) {
        return !this.items.isPresent() || this.items.get().test(component.items());
    }
}
