package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityPaintingMotiveFix extends NamedEntityFix {
    private static final Map<String, String> MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        map.put("donkeykong", "donkey_kong");
        map.put("burningskull", "burning_skull");
        map.put("skullandroses", "skull_and_roses");
    });

    public EntityPaintingMotiveFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityPaintingMotiveFix", References.ENTITY, "minecraft:painting");
    }

    public Dynamic<?> fixTag(Dynamic<?> paintingdynamic) {
        Optional<String> optional = paintingdynamic.get("Motive").asString().result();
        if (optional.isPresent()) {
            String string = optional.get().toLowerCase(Locale.ROOT);
            return paintingdynamic.set("Motive", paintingdynamic.createString(NamespacedSchema.ensureNamespaced(MAP.getOrDefault(string, string))));
        } else {
            return paintingdynamic;
        }
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
