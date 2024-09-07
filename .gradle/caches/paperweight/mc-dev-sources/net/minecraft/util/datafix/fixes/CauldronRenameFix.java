package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class CauldronRenameFix extends DataFix {
    public CauldronRenameFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private static Dynamic<?> fix(Dynamic<?> cauldronDynamic) {
        Optional<String> optional = cauldronDynamic.get("Name").asString().result();
        if (optional.equals(Optional.of("minecraft:cauldron"))) {
            Dynamic<?> dynamic = cauldronDynamic.get("Properties").orElseEmptyMap();
            return dynamic.get("level").asString("0").equals("0")
                ? cauldronDynamic.remove("Properties")
                : cauldronDynamic.set("Name", cauldronDynamic.createString("minecraft:water_cauldron"));
        } else {
            return cauldronDynamic;
        }
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "cauldron_rename_fix", this.getInputSchema().getType(References.BLOCK_STATE), typed -> typed.update(DSL.remainderFinder(), CauldronRenameFix::fix)
        );
    }
}
