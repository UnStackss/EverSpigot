package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RenameEnchantmentsFix extends DataFix {
    final String name;
    final Map<String, String> renames;

    public RenameEnchantmentsFix(Schema outputSchema, String name, Map<String, String> oldToNewIds) {
        super(outputSchema, false);
        this.name = name;
        this.renames = oldToNewIds;
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            this.name,
            type,
            itemStackTyped -> itemStackTyped.updateTyped(opticFinder, itemTagTyped -> itemTagTyped.update(DSL.remainderFinder(), this::fixTag))
        );
    }

    private Dynamic<?> fixTag(Dynamic<?> itemTagDynamic) {
        itemTagDynamic = this.fixEnchantmentList(itemTagDynamic, "Enchantments");
        return this.fixEnchantmentList(itemTagDynamic, "StoredEnchantments");
    }

    private Dynamic<?> fixEnchantmentList(Dynamic<?> itemTagDynamic, String enchantmentsKey) {
        return itemTagDynamic.update(
            enchantmentsKey,
            enchantmentsDynamic -> enchantmentsDynamic.asStreamOpt()
                    .map(
                        enchantments -> enchantments.map(
                                enchantmentDynamic -> enchantmentDynamic.update(
                                        "id",
                                        idDynamic -> idDynamic.asString()
                                                .map(
                                                    oldId -> enchantmentDynamic.createString(
                                                            this.renames.getOrDefault(NamespacedSchema.ensureNamespaced(oldId), oldId)
                                                        )
                                                )
                                                .mapOrElse(Function.identity(), error -> idDynamic)
                                    )
                            )
                    )
                    .map(enchantmentsDynamic::createList)
                    .mapOrElse(Function.identity(), error -> enchantmentsDynamic)
        );
    }
}
