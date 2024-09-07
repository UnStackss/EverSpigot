package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class EntityArmorStandSilentFix extends NamedEntityFix {
    public EntityArmorStandSilentFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityArmorStandSilentFix", References.ENTITY, "ArmorStand");
    }

    public Dynamic<?> fixTag(Dynamic<?> armorStandDynamic) {
        return armorStandDynamic.get("Silent").asBoolean(false) && !armorStandDynamic.get("Marker").asBoolean(false)
            ? armorStandDynamic.remove("Silent")
            : armorStandDynamic;
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
