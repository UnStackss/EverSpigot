package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsProgrammerArtFix extends DataFix {
    public OptionsProgrammerArtFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsProgrammerArtFix",
            this.getInputSchema().getType(References.OPTIONS),
            typed -> typed.update(
                    DSL.remainderFinder(), options -> options.update("resourcePacks", this::fixList).update("incompatibleResourcePacks", this::fixList)
                )
        );
    }

    private <T> Dynamic<T> fixList(Dynamic<T> option) {
        return option.asString().result().map(value -> option.createString(value.replace("\"programer_art\"", "\"programmer_art\""))).orElse(option);
    }
}
