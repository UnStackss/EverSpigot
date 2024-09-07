package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import java.util.Map;

public class VariantRenameFix extends NamedEntityFix {
    private final Map<String, String> renames;

    public VariantRenameFix(Schema outputSchema, String name, TypeReference type, String choiceName, Map<String, String> oldToNewNames) {
        super(outputSchema, false, name, type, choiceName);
        this.renames = oldToNewNames;
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                    "variant",
                    variant -> DataFixUtils.orElse(
                            variant.asString().map(variantName -> variant.createString(this.renames.getOrDefault(variantName, variantName))).result(), variant
                        )
                )
        );
    }
}
