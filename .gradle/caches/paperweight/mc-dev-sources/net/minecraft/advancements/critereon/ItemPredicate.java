package net.minecraft.advancements.critereon;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemPredicate(
    Optional<HolderSet<Item>> items, MinMaxBounds.Ints count, DataComponentPredicate components, Map<ItemSubPredicate.Type<?>, ItemSubPredicate> subPredicates
) implements Predicate<ItemStack> {
    public static final Codec<ItemPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(ItemPredicate::count),
                    DataComponentPredicate.CODEC.optionalFieldOf("components", DataComponentPredicate.EMPTY).forGetter(ItemPredicate::components),
                    ItemSubPredicate.CODEC.optionalFieldOf("predicates", Map.of()).forGetter(ItemPredicate::subPredicates)
                )
                .apply(instance, ItemPredicate::new)
    );

    @Override
    public boolean test(ItemStack stack) {
        if (this.items.isPresent() && !stack.is(this.items.get())) {
            return false;
        } else if (!this.count.matches(stack.getCount())) {
            return false;
        } else if (!this.components.test((DataComponentHolder)stack)) {
            return false;
        } else {
            for (ItemSubPredicate itemSubPredicate : this.subPredicates.values()) {
                if (!itemSubPredicate.matches(stack)) {
                    return false;
                }
            }

            return true;
        }
    }

    public static class Builder {
        private Optional<HolderSet<Item>> items = Optional.empty();
        private MinMaxBounds.Ints count = MinMaxBounds.Ints.ANY;
        private DataComponentPredicate components = DataComponentPredicate.EMPTY;
        private final ImmutableMap.Builder<ItemSubPredicate.Type<?>, ItemSubPredicate> subPredicates = ImmutableMap.builder();

        private Builder() {
        }

        public static ItemPredicate.Builder item() {
            return new ItemPredicate.Builder();
        }

        public ItemPredicate.Builder of(ItemLike... items) {
            this.items = Optional.of(HolderSet.direct(item -> item.asItem().builtInRegistryHolder(), items));
            return this;
        }

        public ItemPredicate.Builder of(TagKey<Item> tag) {
            this.items = Optional.of(BuiltInRegistries.ITEM.getOrCreateTag(tag));
            return this;
        }

        public ItemPredicate.Builder withCount(MinMaxBounds.Ints count) {
            this.count = count;
            return this;
        }

        public <T extends ItemSubPredicate> ItemPredicate.Builder withSubPredicate(ItemSubPredicate.Type<T> type, T subPredicate) {
            this.subPredicates.put(type, subPredicate);
            return this;
        }

        public ItemPredicate.Builder hasComponents(DataComponentPredicate componentPredicate) {
            this.components = componentPredicate;
            return this;
        }

        public ItemPredicate build() {
            return new ItemPredicate(this.items, this.count, this.components, this.subPredicates.build());
        }
    }
}
