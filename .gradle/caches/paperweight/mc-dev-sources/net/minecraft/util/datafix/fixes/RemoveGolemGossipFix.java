package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class RemoveGolemGossipFix extends NamedEntityFix {
    public RemoveGolemGossipFix(Schema outputSchema, boolean changesTyped) {
        super(outputSchema, changesTyped, "Remove Golem Gossip Fix", References.ENTITY, "minecraft:villager");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), RemoveGolemGossipFix::fixValue);
    }

    private static Dynamic<?> fixValue(Dynamic<?> villagerData) {
        return villagerData.update(
            "Gossips",
            gossipsDynamic -> villagerData.createList(
                    gossipsDynamic.asStream().filter(gossipDynamic -> !gossipDynamic.get("Type").asString("").equals("golem"))
                )
        );
    }
}
