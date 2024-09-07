package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemWrittenBookPagesStrictJsonFix extends DataFix {
    public ItemWrittenBookPagesStrictJsonFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public Dynamic<?> fixTag(Dynamic<?> tagDynamic) {
        return tagDynamic.update(
            "pages",
            pagesDynamic -> DataFixUtils.orElse(
                    pagesDynamic.asStreamOpt().map(pages -> pages.map(ComponentDataFixUtils::rewriteFromLenient)).map(tagDynamic::createList).result(),
                    tagDynamic.emptyList()
                )
        );
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemWrittenBookPagesStrictJsonFix",
            type,
            itemStackTyped -> itemStackTyped.updateTyped(opticFinder, tagTyped -> tagTyped.update(DSL.remainderFinder(), this::fixTag))
        );
    }
}
