package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.OptionalDynamic;
import java.util.List;

public class EntityRedundantChanceTagsFix extends DataFix {
    private static final Codec<List<Float>> FLOAT_LIST_CODEC = Codec.FLOAT.listOf();

    public EntityRedundantChanceTagsFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityRedundantChanceTagsFix", this.getInputSchema().getType(References.ENTITY), typed -> typed.update(DSL.remainderFinder(), entityTyped -> {
                    if (isZeroList(entityTyped.get("HandDropChances"), 2)) {
                        entityTyped = entityTyped.remove("HandDropChances");
                    }

                    if (isZeroList(entityTyped.get("ArmorDropChances"), 4)) {
                        entityTyped = entityTyped.remove("ArmorDropChances");
                    }

                    return entityTyped;
                })
        );
    }

    private static boolean isZeroList(OptionalDynamic<?> listTag, int expectedLength) {
        return listTag.flatMap(FLOAT_LIST_CODEC::parse)
            .map(chances -> chances.size() == expectedLength && chances.stream().allMatch(chance -> chance == 0.0F))
            .result()
            .orElse(false);
    }
}
