package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.UnaryOperator;

public class CriteriaRenameFix extends DataFix {
    private final String name;
    private final String advancementId;
    private final UnaryOperator<String> conversions;

    public CriteriaRenameFix(Schema outputSchema, String description, String advancementId, UnaryOperator<String> renamer) {
        super(outputSchema, false);
        this.name = description;
        this.advancementId = advancementId;
        this.conversions = renamer;
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.name, this.getInputSchema().getType(References.ADVANCEMENTS), typed -> typed.update(DSL.remainderFinder(), this::fixAdvancements)
        );
    }

    private Dynamic<?> fixAdvancements(Dynamic<?> advancements) {
        return advancements.update(
            this.advancementId,
            advancement -> advancement.update(
                    "criteria",
                    criteria -> criteria.updateMapValues(
                            pair -> pair.mapFirst(
                                    key -> DataFixUtils.orElse(
                                            key.asString().map(keyString -> key.createString(this.conversions.apply(keyString))).result(), key
                                        )
                                )
                        )
                )
        );
    }
}
