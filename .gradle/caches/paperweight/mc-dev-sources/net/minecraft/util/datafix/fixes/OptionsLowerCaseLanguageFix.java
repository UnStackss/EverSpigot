package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.Locale;
import java.util.Optional;

public class OptionsLowerCaseLanguageFix extends DataFix {
    public OptionsLowerCaseLanguageFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsLowerCaseLanguageFix",
            this.getInputSchema().getType(References.OPTIONS),
            optionsTyped -> optionsTyped.update(
                    DSL.remainderFinder(),
                    optionsDynamic -> {
                        Optional<String> optional = optionsDynamic.get("lang").asString().result();
                        return optional.isPresent()
                            ? optionsDynamic.set("lang", optionsDynamic.createString(optional.get().toLowerCase(Locale.ROOT)))
                            : optionsDynamic;
                    }
                )
        );
    }
}
