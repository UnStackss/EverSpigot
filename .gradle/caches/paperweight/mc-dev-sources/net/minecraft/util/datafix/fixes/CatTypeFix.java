package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class CatTypeFix extends NamedEntityFix {
    public CatTypeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "CatTypeFix", References.ENTITY, "minecraft:cat");
    }

    public Dynamic<?> fixTag(Dynamic<?> catDynamic) {
        return catDynamic.get("CatType").asInt(0) == 9 ? catDynamic.set("CatType", catDynamic.createInt(10)) : catDynamic;
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
