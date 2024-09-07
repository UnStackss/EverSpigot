package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products.P1;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.ConditionUserBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootPoolEntryContainer implements ComposableEntryContainer {
    protected final List<LootItemCondition> conditions;
    private final Predicate<LootContext> compositeCondition;

    protected LootPoolEntryContainer(List<LootItemCondition> conditions) {
        this.conditions = conditions;
        this.compositeCondition = Util.allOf(conditions);
    }

    protected static <T extends LootPoolEntryContainer> P1<Mu<T>, List<LootItemCondition>> commonFields(Instance<T> instance) {
        return instance.group(LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(entry -> entry.conditions));
    }

    public void validate(ValidationContext reporter) {
        for (int i = 0; i < this.conditions.size(); i++) {
            this.conditions.get(i).validate(reporter.forChild(".condition[" + i + "]"));
        }
    }

    protected final boolean canRun(LootContext context) {
        return this.compositeCondition.test(context);
    }

    public abstract LootPoolEntryType getType();

    public abstract static class Builder<T extends LootPoolEntryContainer.Builder<T>> implements ConditionUserBuilder<T> {
        private final ImmutableList.Builder<LootItemCondition> conditions = ImmutableList.builder();

        protected abstract T getThis();

        @Override
        public T when(LootItemCondition.Builder builder) {
            this.conditions.add(builder.build());
            return this.getThis();
        }

        @Override
        public final T unwrap() {
            return this.getThis();
        }

        protected List<LootItemCondition> getConditions() {
            return this.conditions.build();
        }

        public AlternativesEntry.Builder otherwise(LootPoolEntryContainer.Builder<?> builder) {
            return new AlternativesEntry.Builder(this, builder);
        }

        public EntryGroup.Builder append(LootPoolEntryContainer.Builder<?> entry) {
            return new EntryGroup.Builder(this, entry);
        }

        public SequentialEntry.Builder then(LootPoolEntryContainer.Builder<?> entry) {
            return new SequentialEntry.Builder(this, entry);
        }

        public abstract LootPoolEntryContainer build();
    }
}
