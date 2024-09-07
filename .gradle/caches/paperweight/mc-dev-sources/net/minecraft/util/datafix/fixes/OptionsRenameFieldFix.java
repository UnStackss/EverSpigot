package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsRenameFieldFix extends DataFix {
    private final String fixName;
    private final String fieldFrom;
    private final String fieldTo;

    public OptionsRenameFieldFix(Schema outputSchema, boolean changesType, String name, String oldName, String newName) {
        super(outputSchema, changesType);
        this.fixName = name;
        this.fieldFrom = oldName;
        this.fieldTo = newName;
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.fixName,
            this.getInputSchema().getType(References.OPTIONS),
            optionsTyped -> optionsTyped.update(
                    DSL.remainderFinder(),
                    optionsDynamic -> DataFixUtils.orElse(
                            optionsDynamic.get(this.fieldFrom)
                                .result()
                                .map(setting -> optionsDynamic.set(this.fieldTo, (Dynamic<?>)setting).remove(this.fieldFrom)),
                            optionsDynamic
                        )
                )
        );
    }
}
