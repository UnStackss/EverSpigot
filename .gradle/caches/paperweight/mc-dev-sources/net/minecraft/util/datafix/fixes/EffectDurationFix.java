package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EffectDurationFix extends DataFix {
    private static final Set<String> ITEM_TYPES = Set.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow");

    public EffectDurationFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<?> opticFinder2 = type.findField("tag");
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(
                "EffectDurationEntity", schema.getType(References.ENTITY), entityTyped -> entityTyped.update(DSL.remainderFinder(), this::updateEntity)
            ),
            this.fixTypeEverywhereTyped(
                "EffectDurationPlayer", schema.getType(References.PLAYER), playerTyped -> playerTyped.update(DSL.remainderFinder(), this::updateEntity)
            ),
            this.fixTypeEverywhereTyped("EffectDurationItem", type, itemStackTyped -> {
                Optional<Pair<String, String>> optional = itemStackTyped.getOptional(opticFinder);
                if (optional.filter(ITEM_TYPES::contains).isPresent()) {
                    Optional<? extends Typed<?>> optional2 = itemStackTyped.getOptionalTyped(opticFinder2);
                    if (optional2.isPresent()) {
                        Dynamic<?> dynamic = optional2.get().get(DSL.remainderFinder());
                        Typed<?> typed = optional2.get().set(DSL.remainderFinder(), dynamic.update("CustomPotionEffects", this::fix));
                        return itemStackTyped.set(opticFinder2, typed);
                    }
                }

                return itemStackTyped;
            })
        );
    }

    private Dynamic<?> fixEffect(Dynamic<?> effectDynamic) {
        return effectDynamic.update("FactorCalculationData", factorCalculationDataDynamic -> {
            int i = factorCalculationDataDynamic.get("effect_changed_timestamp").asInt(-1);
            factorCalculationDataDynamic = factorCalculationDataDynamic.remove("effect_changed_timestamp");
            int j = effectDynamic.get("Duration").asInt(-1);
            int k = i - j;
            return factorCalculationDataDynamic.set("ticks_active", factorCalculationDataDynamic.createInt(k));
        });
    }

    private Dynamic<?> fix(Dynamic<?> effectsDynamic) {
        return effectsDynamic.createList(effectsDynamic.asStream().map(this::fixEffect));
    }

    private Dynamic<?> updateEntity(Dynamic<?> entityDynamic) {
        entityDynamic = entityDynamic.update("Effects", this::fix);
        entityDynamic = entityDynamic.update("ActiveEffects", this::fix);
        return entityDynamic.update("CustomPotionEffects", this::fix);
    }
}
