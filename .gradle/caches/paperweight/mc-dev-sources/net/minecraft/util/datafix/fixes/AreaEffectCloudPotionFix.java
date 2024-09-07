package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class AreaEffectCloudPotionFix extends NamedEntityFix {
    public AreaEffectCloudPotionFix(Schema outputSchema) {
        super(outputSchema, false, "AreaEffectCloudPotionFix", References.ENTITY, "minecraft:area_effect_cloud");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> areaEffectCloudDynamic) {
        Optional<Dynamic<T>> optional = areaEffectCloudDynamic.get("Color").result();
        Optional<Dynamic<T>> optional2 = areaEffectCloudDynamic.get("effects").result();
        Optional<Dynamic<T>> optional3 = areaEffectCloudDynamic.get("Potion").result();
        areaEffectCloudDynamic = areaEffectCloudDynamic.remove("Color").remove("effects").remove("Potion");
        if (optional.isEmpty() && optional2.isEmpty() && optional3.isEmpty()) {
            return areaEffectCloudDynamic;
        } else {
            Dynamic<T> dynamic = areaEffectCloudDynamic.emptyMap();
            if (optional.isPresent()) {
                dynamic = dynamic.set("custom_color", optional.get());
            }

            if (optional2.isPresent()) {
                dynamic = dynamic.set("custom_effects", optional2.get());
            }

            if (optional3.isPresent()) {
                dynamic = dynamic.set("potion", optional3.get());
            }

            return areaEffectCloudDynamic.set("potion_contents", dynamic);
        }
    }
}
