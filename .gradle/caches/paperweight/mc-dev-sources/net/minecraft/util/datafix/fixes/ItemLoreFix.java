package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemLoreFix extends DataFix {
    public ItemLoreFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "Item Lore componentize",
            type,
            itemStackTyped -> itemStackTyped.updateTyped(
                    opticFinder,
                    tagTyped -> tagTyped.update(
                            DSL.remainderFinder(),
                            tagDynamic -> tagDynamic.update(
                                    "display",
                                    displaySubtag -> displaySubtag.update(
                                            "Lore",
                                            lore -> DataFixUtils.orElse(lore.asStreamOpt().map(ItemLoreFix::fixLoreList).map(lore::createList).result(), lore)
                                        )
                                )
                        )
                )
        );
    }

    private static <T> Stream<Dynamic<T>> fixLoreList(Stream<Dynamic<T>> nbt) {
        return nbt.map(ComponentDataFixUtils::wrapLiteralStringAsComponent);
    }
}
