package net.minecraft.world.level.storage.loot.predicates;

import java.util.function.Function;

public interface ConditionUserBuilder<T extends ConditionUserBuilder<T>> {
    T when(LootItemCondition.Builder condition);

    default <E> T when(Iterable<E> conditions, Function<E, LootItemCondition.Builder> toBuilderFunction) {
        T conditionUserBuilder = this.unwrap();

        for (E object : conditions) {
            conditionUserBuilder = conditionUserBuilder.when(toBuilderFunction.apply(object));
        }

        return conditionUserBuilder;
    }

    T unwrap();
}
