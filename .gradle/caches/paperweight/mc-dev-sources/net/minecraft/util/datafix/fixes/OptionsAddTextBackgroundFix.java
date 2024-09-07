package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsAddTextBackgroundFix extends DataFix {
    public OptionsAddTextBackgroundFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsAddTextBackgroundFix",
            this.getInputSchema().getType(References.OPTIONS),
            optionsTyped -> optionsTyped.update(
                    DSL.remainderFinder(),
                    optionsDynamic -> DataFixUtils.orElse(
                            optionsDynamic.get("chatOpacity")
                                .asString()
                                .map(string -> optionsDynamic.set("textBackgroundOpacity", optionsDynamic.createDouble(this.calculateBackground(string))))
                                .result(),
                            optionsDynamic
                        )
                )
        );
    }

    private double calculateBackground(String chatOpacity) {
        try {
            double d = 0.9 * Double.parseDouble(chatOpacity) + 0.1;
            return d / 2.0;
        } catch (NumberFormatException var4) {
            return 0.5;
        }
    }
}
