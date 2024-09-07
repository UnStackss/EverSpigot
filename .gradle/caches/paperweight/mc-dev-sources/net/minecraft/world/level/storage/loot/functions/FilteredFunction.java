package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class FilteredFunction extends LootItemConditionalFunction {
    public static final MapCodec<FilteredFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        ItemPredicate.CODEC.fieldOf("item_filter").forGetter(lootFunction -> lootFunction.filter),
                        LootItemFunctions.ROOT_CODEC.fieldOf("modifier").forGetter(lootFunction -> lootFunction.modifier)
                    )
                )
                .apply(instance, FilteredFunction::new)
    );
    private final ItemPredicate filter;
    private final LootItemFunction modifier;

    private FilteredFunction(List<LootItemCondition> conditions, ItemPredicate itemFilter, LootItemFunction modifier) {
        super(conditions);
        this.filter = itemFilter;
        this.modifier = modifier;
    }

    @Override
    public LootItemFunctionType<FilteredFunction> getType() {
        return LootItemFunctions.FILTERED;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        return this.filter.test(stack) ? this.modifier.apply(stack, context) : stack;
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);
        this.modifier.validate(reporter.forChild(".modifier"));
    }
}
