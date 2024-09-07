package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class IglooMetadataRemovalFix extends DataFix {
    public IglooMetadataRemovalFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.STRUCTURE_FEATURE);
        return this.fixTypeEverywhereTyped(
            "IglooMetadataRemovalFix", type, structureFeatureTyped -> structureFeatureTyped.update(DSL.remainderFinder(), IglooMetadataRemovalFix::fixTag)
        );
    }

    private static <T> Dynamic<T> fixTag(Dynamic<T> structureFeatureDynamic) {
        boolean bl = structureFeatureDynamic.get("Children")
            .asStreamOpt()
            .map(stream -> stream.allMatch(IglooMetadataRemovalFix::isIglooPiece))
            .result()
            .orElse(false);
        return bl
            ? structureFeatureDynamic.set("id", structureFeatureDynamic.createString("Igloo")).remove("Children")
            : structureFeatureDynamic.update("Children", IglooMetadataRemovalFix::removeIglooPieces);
    }

    private static <T> Dynamic<T> removeIglooPieces(Dynamic<T> structureFeatureDynamic) {
        return structureFeatureDynamic.asStreamOpt()
            .map(stream -> stream.filter(dynamic -> !isIglooPiece((Dynamic<?>)dynamic)))
            .map(structureFeatureDynamic::createList)
            .result()
            .orElse(structureFeatureDynamic);
    }

    private static boolean isIglooPiece(Dynamic<?> structureFeatureDynamic) {
        return structureFeatureDynamic.get("id").asString("").equals("Iglu");
    }
}
