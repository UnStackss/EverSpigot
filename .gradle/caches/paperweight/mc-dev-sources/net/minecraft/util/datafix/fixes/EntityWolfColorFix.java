package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityWolfColorFix extends NamedEntityFix {
    public EntityWolfColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityWolfColorFix", References.ENTITY, "minecraft:wolf");
    }

    public Dynamic<?> fixTag(Dynamic<?> wolfDynamic) {
        return wolfDynamic.update("CollarColor", colorDynamic -> colorDynamic.createByte((byte)(15 - colorDynamic.asInt(0))));
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
