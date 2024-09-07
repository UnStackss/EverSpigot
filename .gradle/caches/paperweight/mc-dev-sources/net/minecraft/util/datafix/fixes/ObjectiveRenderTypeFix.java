package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.Optional;

public class ObjectiveRenderTypeFix extends DataFix {
    public ObjectiveRenderTypeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private static String getRenderType(String oldName) {
        return oldName.equals("health") ? "hearts" : "integer";
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.OBJECTIVE);
        return this.fixTypeEverywhereTyped("ObjectiveRenderTypeFix", type, typed -> typed.update(DSL.remainderFinder(), objective -> {
                Optional<String> optional = objective.get("RenderType").asString().result();
                if (optional.isEmpty()) {
                    String string = objective.get("CriteriaName").asString("");
                    String string2 = getRenderType(string);
                    return objective.set("RenderType", objective.createString(string2));
                } else {
                    return objective;
                }
            }));
    }
}
