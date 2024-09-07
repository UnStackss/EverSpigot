package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.function.Function;
import java.util.function.IntFunction;

public class EntityVariantFix extends NamedEntityFix {
    private final String fieldName;
    private final IntFunction<String> idConversions;

    public EntityVariantFix(Schema outputSchema, String name, TypeReference type, String entityId, String variantKey, IntFunction<String> variantIntToId) {
        super(outputSchema, false, name, type, entityId);
        this.fieldName = variantKey;
        this.idConversions = variantIntToId;
    }

    private static <T> Dynamic<T> updateAndRename(
        Dynamic<T> entityDynamic, String oldVariantKey, String newVariantKey, Function<Dynamic<T>, Dynamic<T>> variantIntToId
    ) {
        return entityDynamic.map(
            object -> {
                DynamicOps<T> dynamicOps = entityDynamic.getOps();
                Function<T, T> function2 = objectx -> variantIntToId.apply(new Dynamic<>(dynamicOps, (T)objectx)).getValue();
                return dynamicOps.get((T)object, oldVariantKey)
                    .map(object2 -> dynamicOps.set((T)object, newVariantKey, function2.apply((T)object2)))
                    .result()
                    .orElse((T)object);
            }
        );
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(
            DSL.remainderFinder(),
            entityDynamic -> updateAndRename(
                    entityDynamic,
                    this.fieldName,
                    "variant",
                    variantDynamic -> DataFixUtils.orElse(
                            variantDynamic.asNumber().map(variantInt -> variantDynamic.createString(this.idConversions.apply(variantInt.intValue()))).result(),
                            variantDynamic
                        )
                )
        );
    }
}
