package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntries;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import org.apache.commons.lang3.mutable.MutableInt;

public class LootPool {
    public static final Codec<LootPool> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    LootPoolEntries.CODEC.listOf().fieldOf("entries").forGetter(pool -> pool.entries),
                    LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(pool -> pool.conditions),
                    LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(pool -> pool.functions),
                    NumberProviders.CODEC.fieldOf("rolls").forGetter(pool -> pool.rolls),
                    NumberProviders.CODEC.fieldOf("bonus_rolls").orElse(ConstantValue.exactly(0.0F)).forGetter(pool -> pool.bonusRolls)
                )
                .apply(instance, LootPool::new)
    );
    private final List<LootPoolEntryContainer> entries;
    private final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;
    private final List<LootItemFunction> functions;
    private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final NumberProvider rolls;
    private final NumberProvider bonusRolls;

    LootPool(
        List<LootPoolEntryContainer> entries,
        List<LootItemCondition> conditions,
        List<LootItemFunction> functions,
        NumberProvider rolls,
        NumberProvider bonusRolls
    ) {
        this.entries = entries;
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
        this.rolls = rolls;
        this.bonusRolls = bonusRolls;
    }

    private void addRandomItem(Consumer<ItemStack> lootConsumer, LootContext context) {
        RandomSource randomSource = context.getRandom();
        List<LootPoolEntry> list = Lists.newArrayList();
        MutableInt mutableInt = new MutableInt();

        for (LootPoolEntryContainer lootPoolEntryContainer : this.entries) {
            lootPoolEntryContainer.expand(context, choice -> {
                int i = choice.getWeight(context.getLuck());
                if (i > 0) {
                    list.add(choice);
                    mutableInt.add(i);
                }
            });
        }

        int i = list.size();
        if (mutableInt.intValue() != 0 && i != 0) {
            if (i == 1) {
                list.get(0).createItemStack(lootConsumer, context);
            } else {
                int j = randomSource.nextInt(mutableInt.intValue());

                for (LootPoolEntry lootPoolEntry : list) {
                    j -= lootPoolEntry.getWeight(context.getLuck());
                    if (j < 0) {
                        lootPoolEntry.createItemStack(lootConsumer, context);
                        return;
                    }
                }
            }
        }
    }

    public void addRandomItems(Consumer<ItemStack> lootConsumer, LootContext context) {
        if (this.compositeCondition.test(context)) {
            Consumer<ItemStack> consumer = LootItemFunction.decorate(this.compositeFunction, lootConsumer, context);
            int i = this.rolls.getInt(context) + Mth.floor(this.bonusRolls.getFloat(context) * context.getLuck());

            for (int j = 0; j < i; j++) {
                this.addRandomItem(consumer, context);
            }
        }
    }

    public void validate(ValidationContext reporter) {
        for (int i = 0; i < this.conditions.size(); i++) {
            this.conditions.get(i).validate(reporter.forChild(".condition[" + i + "]"));
        }

        for (int j = 0; j < this.functions.size(); j++) {
            this.functions.get(j).validate(reporter.forChild(".functions[" + j + "]"));
        }

        for (int k = 0; k < this.entries.size(); k++) {
            this.entries.get(k).validate(reporter.forChild(".entries[" + k + "]"));
        }

        this.rolls.validate(reporter.forChild(".rolls"));
        this.bonusRolls.validate(reporter.forChild(".bonusRolls"));
    }

    public static LootPool.Builder lootPool() {
        return new LootPool.Builder();
    }

    public static class Builder implements FunctionUserBuilder<LootPool.Builder>, ConditionUserBuilder<LootPool.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();
        private NumberProvider rolls = ConstantValue.exactly(1.0F);
        private NumberProvider bonusRolls = ConstantValue.exactly(0.0F);

        public LootPool.Builder setRolls(NumberProvider rolls) {
            this.rolls = rolls;
            return this;
        }

        @Override
        public LootPool.Builder unwrap() {
            return this;
        }

        public LootPool.Builder setBonusRolls(NumberProvider bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }

        public LootPool.Builder add(LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        @Override
        public LootPool.Builder when(LootItemCondition.Builder builder) {
            this.conditions.add(builder.build());
            return this;
        }

        @Override
        public LootPool.Builder apply(LootItemFunction.Builder builder) {
            this.functions.add(builder.build());
            return this;
        }

        public LootPool build() {
            return new LootPool(this.entries.build(), this.conditions.build(), this.functions.build(), this.rolls, this.bonusRolls);
        }
    }
}
