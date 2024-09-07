package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemStackUUIDFix extends AbstractUUIDFix {
    public ItemStackUUIDFix(Schema outputSchema) {
        super(outputSchema, References.ITEM_STACK);
    }

    public TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        return this.fixTypeEverywhereTyped("ItemStackUUIDFix", this.getInputSchema().getType(this.typeReference), itemStackTyped -> {
            OpticFinder<?> opticFinder2 = itemStackTyped.getType().findField("tag");
            return itemStackTyped.updateTyped(opticFinder2, tagTyped -> tagTyped.update(DSL.remainderFinder(), tagDynamic -> {
                    tagDynamic = this.updateAttributeModifiers(tagDynamic);
                    if (itemStackTyped.getOptional(opticFinder).map(id -> "minecraft:player_head".equals(id.getSecond())).orElse(false)) {
                        tagDynamic = this.updateSkullOwner(tagDynamic);
                    }

                    return tagDynamic;
                }));
        });
    }

    private Dynamic<?> updateAttributeModifiers(Dynamic<?> tagDynamic) {
        return tagDynamic.update(
            "AttributeModifiers",
            attributeModifiersDynamic -> tagDynamic.createList(
                    attributeModifiersDynamic.asStream()
                        .map(attributeModifier -> replaceUUIDLeastMost((Dynamic<?>)attributeModifier, "UUID", "UUID").orElse((Dynamic<?>)attributeModifier))
                )
        );
    }

    private Dynamic<?> updateSkullOwner(Dynamic<?> tagDynamic) {
        return tagDynamic.update("SkullOwner", skullOwner -> replaceUUIDString(skullOwner, "Id", "Id").orElse(skullOwner));
    }
}
