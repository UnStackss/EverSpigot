package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class ChunkLightRemoveFix extends DataFix {
    public ChunkLightRemoveFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type2 = type.findFieldType("Level");
        OpticFinder<?> opticFinder = DSL.fieldFinder("Level", type2);
        return this.fixTypeEverywhereTyped(
            "ChunkLightRemoveFix",
            type,
            this.getOutputSchema().getType(References.CHUNK),
            chunkTyped -> chunkTyped.updateTyped(
                    opticFinder, levelTyped -> levelTyped.update(DSL.remainderFinder(), levelDynamic -> levelDynamic.remove("isLightOn"))
                )
        );
    }
}
