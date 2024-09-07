package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public abstract class ItemStackTagFix extends DataFix {
    private final String name;
    private final Predicate<String> idFilter;

    public ItemStackTagFix(Schema outputSchema, String name, Predicate<String> itemIdPredicate) {
        super(outputSchema, false);
        this.name = name;
        this.idFilter = itemIdPredicate;
    }

    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        return this.fixTypeEverywhereTyped(this.name, type, createFixer(type, this.idFilter, this::fixItemStackTag));
    }

    public static UnaryOperator<Typed<?>> createFixer(Type<?> itemStackType, Predicate<String> itemIdPredicate, UnaryOperator<Dynamic<?>> nbtFixer) {
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder2 = itemStackType.findField("tag");
        return itemStackTyped -> {
            Optional<Pair<String, String>> optional = itemStackTyped.getOptional(opticFinder);
            return optional.isPresent() && itemIdPredicate.test(optional.get().getSecond())
                ? itemStackTyped.updateTyped(opticFinder2, tag -> tag.update(DSL.remainderFinder(), nbtFixer))
                : itemStackTyped;
        };
    }

    protected abstract <T> Dynamic<T> fixItemStackTag(Dynamic<T> dynamic);
}
