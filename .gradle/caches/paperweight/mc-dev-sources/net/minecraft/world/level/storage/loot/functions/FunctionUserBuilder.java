package net.minecraft.world.level.storage.loot.functions;

import java.util.Arrays;
import java.util.function.Function;

public interface FunctionUserBuilder<T extends FunctionUserBuilder<T>> {
    T apply(LootItemFunction.Builder function);

    default <E> T apply(Iterable<E> functions, Function<E, LootItemFunction.Builder> toBuilderFunction) {
        T functionUserBuilder = this.unwrap();

        for (E object : functions) {
            functionUserBuilder = functionUserBuilder.apply(toBuilderFunction.apply(object));
        }

        return functionUserBuilder;
    }

    default <E> T apply(E[] functions, Function<E, LootItemFunction.Builder> toBuilderFunction) {
        return this.apply(Arrays.asList(functions), toBuilderFunction);
    }

    T unwrap();
}
