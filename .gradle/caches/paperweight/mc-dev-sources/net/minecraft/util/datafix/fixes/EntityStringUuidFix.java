package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import java.util.Optional;
import java.util.UUID;

public class EntityStringUuidFix extends DataFix {
    public EntityStringUuidFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityStringUuidFix",
            this.getInputSchema().getType(References.ENTITY),
            entityTyped -> entityTyped.update(
                    DSL.remainderFinder(),
                    entityDynamic -> {
                        Optional<String> optional = entityDynamic.get("UUID").asString().result();
                        if (optional.isPresent()) {
                            UUID uUID = UUID.fromString(optional.get());
                            return entityDynamic.remove("UUID")
                                .set("UUIDMost", entityDynamic.createLong(uUID.getMostSignificantBits()))
                                .set("UUIDLeast", entityDynamic.createLong(uUID.getLeastSignificantBits()));
                        } else {
                            return entityDynamic;
                        }
                    }
                )
        );
    }
}
