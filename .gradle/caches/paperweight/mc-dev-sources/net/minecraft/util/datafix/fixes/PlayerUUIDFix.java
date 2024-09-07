package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class PlayerUUIDFix extends AbstractUUIDFix {
    public PlayerUUIDFix(Schema outputSchema) {
        super(outputSchema, References.PLAYER);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "PlayerUUIDFix",
            this.getInputSchema().getType(this.typeReference),
            playerTyped -> {
                OpticFinder<?> opticFinder = playerTyped.getType().findField("RootVehicle");
                return playerTyped.updateTyped(
                        opticFinder,
                        opticFinder.type(),
                        rootVehicleTyped -> rootVehicleTyped.update(
                                DSL.remainderFinder(),
                                rootVehicleDynamic -> replaceUUIDLeastMost(rootVehicleDynamic, "Attach", "Attach").orElse(rootVehicleDynamic)
                            )
                    )
                    .update(DSL.remainderFinder(), playerDynamic -> EntityUUIDFix.updateEntityUUID(EntityUUIDFix.updateLivingEntity(playerDynamic)));
            }
        );
    }
}
