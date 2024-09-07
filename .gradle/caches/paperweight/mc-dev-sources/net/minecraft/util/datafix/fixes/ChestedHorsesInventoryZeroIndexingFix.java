package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;

public class ChestedHorsesInventoryZeroIndexingFix extends DataFix {
    public ChestedHorsesInventoryZeroIndexingFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> opticFinder = DSL.typeFinder(
            (Type<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>>)this.getInputSchema()
                .getType(References.ITEM_STACK)
        );
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        return TypeRewriteRule.seq(
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:llama"),
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:trader_llama"),
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:mule"),
            this.horseLikeInventoryIndexingFixer(opticFinder, type, "minecraft:donkey")
        );
    }

    private TypeRewriteRule horseLikeInventoryIndexingFixer(
        OpticFinder<Pair<String, Pair<Either<Pair<String, String>, Unit>, Pair<Either<?, Unit>, Dynamic<?>>>>> itemStackOpticFinder,
        Type<?> entityType,
        String entityId
    ) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, entityId);
        OpticFinder<?> opticFinder = DSL.namedChoice(entityId, type);
        OpticFinder<?> opticFinder2 = type.findField("Items");
        return this.fixTypeEverywhereTyped(
            "Fix non-zero indexing in chest horse type " + entityId,
            entityType,
            entityTyped -> entityTyped.updateTyped(
                    opticFinder,
                    specificEntityTyped -> specificEntityTyped.updateTyped(
                            opticFinder2,
                            entityItemsTyped -> entityItemsTyped.update(
                                    itemStackOpticFinder,
                                    itemStackEntry -> itemStackEntry.mapSecond(
                                            pair -> pair.mapSecond(
                                                    pairx -> pairx.mapSecond(
                                                            itemStackDynamic -> itemStackDynamic.update(
                                                                    "Slot", slotDynamic -> slotDynamic.createByte((byte)(slotDynamic.asInt(2) - 2))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
