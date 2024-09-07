package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class GossipUUIDFix extends NamedEntityFix {
    public GossipUUIDFix(Schema outputSchema, String choiceType) {
        super(outputSchema, false, "Gossip for for " + choiceType, References.ENTITY, choiceType);
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(
            DSL.remainderFinder(),
            entityDynamic -> entityDynamic.update(
                    "Gossips",
                    gossipsDynamic -> DataFixUtils.orElse(
                            gossipsDynamic.asStreamOpt()
                                .result()
                                .map(
                                    gossips -> gossips.map(
                                            gossipDynamic -> AbstractUUIDFix.replaceUUIDLeastMost((Dynamic<?>)gossipDynamic, "Target", "Target")
                                                    .orElse((Dynamic<?>)gossipDynamic)
                                        )
                                )
                                .map(gossipsDynamic::createList),
                            gossipsDynamic
                        )
                )
        );
    }
}
