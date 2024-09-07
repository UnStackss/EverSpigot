package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityUUIDFix extends AbstractUUIDFix {
    public BlockEntityUUIDFix(Schema outputSchema) {
        super(outputSchema, References.BLOCK_ENTITY);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("BlockEntityUUIDFix", this.getInputSchema().getType(this.typeReference), typed -> {
            typed = this.updateNamedChoice(typed, "minecraft:conduit", this::updateConduit);
            return this.updateNamedChoice(typed, "minecraft:skull", this::updateSkull);
        });
    }

    private Dynamic<?> updateSkull(Dynamic<?> skullDynamic) {
        return skullDynamic.get("Owner")
            .get()
            .map(ownerDynamic -> replaceUUIDString((Dynamic<?>)ownerDynamic, "Id", "Id").orElse((Dynamic<?>)ownerDynamic))
            .map(ownerDynamic -> skullDynamic.remove("Owner").set("SkullOwner", (Dynamic<?>)ownerDynamic))
            .result()
            .orElse(skullDynamic);
    }

    private Dynamic<?> updateConduit(Dynamic<?> conduitDynamic) {
        return replaceUUIDMLTag(conduitDynamic, "target_uuid", "Target").orElse(conduitDynamic);
    }
}
