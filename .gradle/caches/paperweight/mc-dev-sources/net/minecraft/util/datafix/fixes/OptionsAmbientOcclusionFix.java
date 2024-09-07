package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsAmbientOcclusionFix extends DataFix {
    public OptionsAmbientOcclusionFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsAmbientOcclusionFix",
            this.getInputSchema().getType(References.OPTIONS),
            optionsTyped -> optionsTyped.update(
                    DSL.remainderFinder(),
                    optionsdynamic -> DataFixUtils.orElse(
                            optionsdynamic.get("ao")
                                .asString()
                                .map(setting -> optionsdynamic.set("ao", optionsdynamic.createString(updateValue(setting))))
                                .result(),
                            optionsdynamic
                        )
                )
        );
    }

    private static String updateValue(String oldValue) {
        return switch (oldValue) {
            case "0" -> "false";
            case "1", "2" -> "true";
            default -> oldValue;
        };
    }
}
