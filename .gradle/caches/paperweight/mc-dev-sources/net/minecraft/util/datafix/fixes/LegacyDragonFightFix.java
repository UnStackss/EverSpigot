package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class LegacyDragonFightFix extends DataFix {
    public LegacyDragonFightFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Dynamic<T> fixDragonFight(Dynamic<T> dynamic) {
        return dynamic.update("ExitPortalLocation", ExtraDataFixUtils::fixBlockPos);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "LegacyDragonFightFix", this.getInputSchema().getType(References.LEVEL), typed -> typed.update(DSL.remainderFinder(), levelData -> {
                    OptionalDynamic<?> optionalDynamic = levelData.get("DragonFight");
                    if (optionalDynamic.result().isPresent()) {
                        return levelData;
                    } else {
                        Dynamic<?> dynamic = levelData.get("DimensionData").get("1").get("DragonFight").orElseEmptyMap();
                        return levelData.set("DragonFight", fixDragonFight(dynamic));
                    }
                })
        );
    }
}
