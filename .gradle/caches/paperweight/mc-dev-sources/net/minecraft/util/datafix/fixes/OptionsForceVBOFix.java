package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsForceVBOFix extends DataFix {
    public OptionsForceVBOFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsForceVBOFix",
            this.getInputSchema().getType(References.OPTIONS),
            optionsTyped -> optionsTyped.update(DSL.remainderFinder(), optionsDynamic -> optionsDynamic.set("useVbo", optionsDynamic.createString("true")))
        );
    }
}
