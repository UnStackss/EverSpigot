package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class VillagerFollowRangeFix extends NamedEntityFix {
    private static final double ORIGINAL_VALUE = 16.0;
    private static final double NEW_BASE_VALUE = 48.0;

    public VillagerFollowRangeFix(Schema outputSchema) {
        super(outputSchema, false, "Villager Follow Range Fix", References.ENTITY, "minecraft:villager");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), VillagerFollowRangeFix::fixValue);
    }

    private static Dynamic<?> fixValue(Dynamic<?> villagerDynamic) {
        return villagerDynamic.update(
            "Attributes",
            attributesDynamic -> villagerDynamic.createList(
                    attributesDynamic.asStream()
                        .map(
                            attributeDynamic -> attributeDynamic.get("Name").asString("").equals("generic.follow_range")
                                        && attributeDynamic.get("Base").asDouble(0.0) == 16.0
                                    ? attributeDynamic.set("Base", attributeDynamic.createDouble(48.0))
                                    : attributeDynamic
                        )
                )
        );
    }
}
