package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class StriderGravityFix extends NamedEntityFix {
    public StriderGravityFix(Schema outputschema, boolean changesType) {
        super(outputschema, changesType, "StriderGravityFix", References.ENTITY, "minecraft:strider");
    }

    public Dynamic<?> fixTag(Dynamic<?> striderDynamic) {
        return striderDynamic.get("NoGravity").asBoolean(false) ? striderDynamic.set("NoGravity", striderDynamic.createBoolean(false)) : striderDynamic;
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
