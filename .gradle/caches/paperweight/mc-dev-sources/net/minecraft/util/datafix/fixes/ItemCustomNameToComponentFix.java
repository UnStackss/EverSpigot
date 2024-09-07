package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemCustomNameToComponentFix extends DataFix {
    public ItemCustomNameToComponentFix(Schema outputSchema, boolean changesTyped) {
        super(outputSchema, changesTyped);
    }

    private Dynamic<?> fixTag(Dynamic<?> tagDynamic) {
        Optional<? extends Dynamic<?>> optional = tagDynamic.get("display").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            Optional<String> optional2 = dynamic.get("Name").asString().result();
            if (optional2.isPresent()) {
                dynamic = dynamic.set("Name", ComponentDataFixUtils.createPlainTextComponent(dynamic.getOps(), optional2.get()));
            }

            return tagDynamic.set("display", dynamic);
        } else {
            return tagDynamic;
        }
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemCustomNameToComponentFix",
            type,
            itemStackTyped -> itemStackTyped.updateTyped(opticFinder, tagTyped -> tagTyped.update(DSL.remainderFinder(), this::fixTag))
        );
    }
}
