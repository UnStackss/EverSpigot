package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class StructureReferenceCountFix extends DataFix {
    public StructureReferenceCountFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.STRUCTURE_FEATURE);
        return this.fixTypeEverywhereTyped(
            "Structure Reference Fix",
            type,
            structureFeatureTyped -> structureFeatureTyped.update(DSL.remainderFinder(), StructureReferenceCountFix::setCountToAtLeastOne)
        );
    }

    private static <T> Dynamic<T> setCountToAtLeastOne(Dynamic<T> structureFeatureDynamic) {
        return structureFeatureDynamic.update(
            "references",
            referencesDynamic -> referencesDynamic.createInt(
                    referencesDynamic.asNumber().map(Number::intValue).result().filter(references -> references > 0).orElse(1)
                )
        );
    }
}
