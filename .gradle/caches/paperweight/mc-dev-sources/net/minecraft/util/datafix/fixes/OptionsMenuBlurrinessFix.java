package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class OptionsMenuBlurrinessFix extends DataFix {
    public OptionsMenuBlurrinessFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsMenuBlurrinessFix",
            this.getInputSchema().getType(References.OPTIONS),
            optionsTyped -> optionsTyped.update(
                    DSL.remainderFinder(),
                    optionsDynamic -> optionsDynamic.update(
                            "menuBackgroundBlurriness",
                            menuBackgroundBlurriness -> menuBackgroundBlurriness.createInt(this.convertToIntRange(menuBackgroundBlurriness.asString("0.5")))
                        )
                )
        );
    }

    private int convertToIntRange(String value) {
        try {
            return Math.round(Float.parseFloat(value) * 10.0F);
        } catch (NumberFormatException var3) {
            return 5;
        }
    }
}
