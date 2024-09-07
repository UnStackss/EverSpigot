package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class MobEffectIdFix extends DataFix {
    private static final Int2ObjectMap<String> ID_MAP = Util.make(new Int2ObjectOpenHashMap<>(), idMap -> {
        idMap.put(1, "minecraft:speed");
        idMap.put(2, "minecraft:slowness");
        idMap.put(3, "minecraft:haste");
        idMap.put(4, "minecraft:mining_fatigue");
        idMap.put(5, "minecraft:strength");
        idMap.put(6, "minecraft:instant_health");
        idMap.put(7, "minecraft:instant_damage");
        idMap.put(8, "minecraft:jump_boost");
        idMap.put(9, "minecraft:nausea");
        idMap.put(10, "minecraft:regeneration");
        idMap.put(11, "minecraft:resistance");
        idMap.put(12, "minecraft:fire_resistance");
        idMap.put(13, "minecraft:water_breathing");
        idMap.put(14, "minecraft:invisibility");
        idMap.put(15, "minecraft:blindness");
        idMap.put(16, "minecraft:night_vision");
        idMap.put(17, "minecraft:hunger");
        idMap.put(18, "minecraft:weakness");
        idMap.put(19, "minecraft:poison");
        idMap.put(20, "minecraft:wither");
        idMap.put(21, "minecraft:health_boost");
        idMap.put(22, "minecraft:absorption");
        idMap.put(23, "minecraft:saturation");
        idMap.put(24, "minecraft:glowing");
        idMap.put(25, "minecraft:levitation");
        idMap.put(26, "minecraft:luck");
        idMap.put(27, "minecraft:unluck");
        idMap.put(28, "minecraft:slow_falling");
        idMap.put(29, "minecraft:conduit_power");
        idMap.put(30, "minecraft:dolphins_grace");
        idMap.put(31, "minecraft:bad_omen");
        idMap.put(32, "minecraft:hero_of_the_village");
        idMap.put(33, "minecraft:darkness");
    });
    private static final Set<String> MOB_EFFECT_INSTANCE_CARRIER_ITEMS = Set.of(
        "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion", "minecraft:tipped_arrow"
    );

    public MobEffectIdFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Optional<Dynamic<T>> getAndConvertMobEffectId(Dynamic<T> dynamic, String idKey) {
        return dynamic.get(idKey).asNumber().result().map(oldId -> ID_MAP.get(oldId.intValue())).map(dynamic::createString);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> dynamic, String oldKey, Dynamic<T> dynamic2, String newKey) {
        Optional<Dynamic<T>> optional = getAndConvertMobEffectId(dynamic, oldKey);
        return dynamic2.replaceField(oldKey, newKey, optional);
    }

    private static <T> Dynamic<T> updateMobEffectIdField(Dynamic<T> dynamic, String oldKey, String newKey) {
        return updateMobEffectIdField(dynamic, oldKey, dynamic, newKey);
    }

    private static <T> Dynamic<T> updateMobEffectInstance(Dynamic<T> effectDynamic) {
        effectDynamic = updateMobEffectIdField(effectDynamic, "Id", "id");
        effectDynamic = effectDynamic.renameField("Ambient", "ambient");
        effectDynamic = effectDynamic.renameField("Amplifier", "amplifier");
        effectDynamic = effectDynamic.renameField("Duration", "duration");
        effectDynamic = effectDynamic.renameField("ShowParticles", "show_particles");
        effectDynamic = effectDynamic.renameField("ShowIcon", "show_icon");
        Optional<Dynamic<T>> optional = effectDynamic.get("HiddenEffect").result().map(MobEffectIdFix::updateMobEffectInstance);
        return effectDynamic.replaceField("HiddenEffect", "hidden_effect", optional);
    }

    private static <T> Dynamic<T> updateMobEffectInstanceList(Dynamic<T> dynamic, String oldEffectListKey, String newEffectListKey) {
        Optional<Dynamic<T>> optional = dynamic.get(oldEffectListKey)
            .asStreamOpt()
            .result()
            .map(oldEffects -> dynamic.createList(oldEffects.map(MobEffectIdFix::updateMobEffectInstance)));
        return dynamic.replaceField(oldEffectListKey, newEffectListKey, optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> effectDynamicIn, Dynamic<T> effectDynamicOut) {
        effectDynamicOut = updateMobEffectIdField(effectDynamicIn, "EffectId", effectDynamicOut, "id");
        Optional<Dynamic<T>> optional = effectDynamicIn.get("EffectDuration").result();
        return effectDynamicOut.replaceField("EffectDuration", "duration", optional);
    }

    private static <T> Dynamic<T> updateSuspiciousStewEntry(Dynamic<T> effectDynamic) {
        return updateSuspiciousStewEntry(effectDynamic, effectDynamic);
    }

    private Typed<?> updateNamedChoice(Typed<?> entityTyped, TypeReference entityTypeReference, String entityId, Function<Dynamic<?>, Dynamic<?>> effectsFixer) {
        Type<?> type = this.getInputSchema().getChoiceType(entityTypeReference, entityId);
        Type<?> type2 = this.getOutputSchema().getChoiceType(entityTypeReference, entityId);
        return entityTyped.updateTyped(
            DSL.namedChoice(entityId, type), type2, matchingEntityTyped -> matchingEntityTyped.update(DSL.remainderFinder(), effectsFixer)
        );
    }

    private TypeRewriteRule blockEntityFixer() {
        Type<?> type = this.getInputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "BlockEntityMobEffectIdFix", type, typed -> this.updateNamedChoice(typed, References.BLOCK_ENTITY, "minecraft:beacon", dynamic -> {
                    dynamic = updateMobEffectIdField(dynamic, "Primary", "primary_effect");
                    return updateMobEffectIdField(dynamic, "Secondary", "secondary_effect");
                })
        );
    }

    private static <T> Dynamic<T> fixMooshroomTag(Dynamic<T> dynamic) {
        Dynamic<T> dynamic2 = dynamic.emptyMap();
        Dynamic<T> dynamic3 = updateSuspiciousStewEntry(dynamic, dynamic2);
        if (!dynamic3.equals(dynamic2)) {
            dynamic = dynamic.set("stew_effects", dynamic.createList(Stream.of(dynamic3)));
        }

        return dynamic.remove("EffectId").remove("EffectDuration");
    }

    private static <T> Dynamic<T> fixArrowTag(Dynamic<T> dynamic) {
        return updateMobEffectInstanceList(dynamic, "CustomPotionEffects", "custom_potion_effects");
    }

    private static <T> Dynamic<T> fixAreaEffectCloudTag(Dynamic<T> dynamic) {
        return updateMobEffectInstanceList(dynamic, "Effects", "effects");
    }

    private static Dynamic<?> updateLivingEntityTag(Dynamic<?> dynamic) {
        return updateMobEffectInstanceList(dynamic, "ActiveEffects", "active_effects");
    }

    private TypeRewriteRule entityFixer() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped("EntityMobEffectIdFix", type, entityTyped -> {
            entityTyped = this.updateNamedChoice(entityTyped, References.ENTITY, "minecraft:mooshroom", MobEffectIdFix::fixMooshroomTag);
            entityTyped = this.updateNamedChoice(entityTyped, References.ENTITY, "minecraft:arrow", MobEffectIdFix::fixArrowTag);
            entityTyped = this.updateNamedChoice(entityTyped, References.ENTITY, "minecraft:area_effect_cloud", MobEffectIdFix::fixAreaEffectCloudTag);
            return entityTyped.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag);
        });
    }

    private TypeRewriteRule playerFixer() {
        Type<?> type = this.getInputSchema().getType(References.PLAYER);
        return this.fixTypeEverywhereTyped("PlayerMobEffectIdFix", type, typed -> typed.update(DSL.remainderFinder(), MobEffectIdFix::updateLivingEntityTag));
    }

    private static <T> Dynamic<T> fixSuspiciousStewTag(Dynamic<T> tagTyped) {
        Optional<Dynamic<T>> optional = tagTyped.get("Effects")
            .asStreamOpt()
            .result()
            .map(effects -> tagTyped.createList(effects.map(MobEffectIdFix::updateSuspiciousStewEntry)));
        return tagTyped.replaceField("Effects", "effects", optional);
    }

    private TypeRewriteRule itemStackFixer() {
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder2 = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemStackMobEffectIdFix",
            type,
            itemStackTyped -> {
                Optional<Pair<String, String>> optional = itemStackTyped.getOptional(opticFinder);
                if (optional.isPresent()) {
                    String string = optional.get().getSecond();
                    if (string.equals("minecraft:suspicious_stew")) {
                        return itemStackTyped.updateTyped(
                            opticFinder2, tagTyped -> tagTyped.update(DSL.remainderFinder(), MobEffectIdFix::fixSuspiciousStewTag)
                        );
                    }

                    if (MOB_EFFECT_INSTANCE_CARRIER_ITEMS.contains(string)) {
                        return itemStackTyped.updateTyped(
                            opticFinder2,
                            tagTyped -> tagTyped.update(
                                    DSL.remainderFinder(),
                                    tagDynamic -> updateMobEffectInstanceList(tagDynamic, "CustomPotionEffects", "custom_potion_effects")
                                )
                        );
                    }
                }

                return itemStackTyped;
            }
        );
    }

    protected TypeRewriteRule makeRule() {
        return TypeRewriteRule.seq(this.blockEntityFixer(), this.entityFixer(), this.playerFixer(), this.itemStackFixer());
    }
}
