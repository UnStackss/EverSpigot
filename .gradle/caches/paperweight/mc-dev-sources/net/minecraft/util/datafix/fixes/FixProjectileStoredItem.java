package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class FixProjectileStoredItem extends DataFix {
    private static final String EMPTY_POTION = "minecraft:empty";

    public FixProjectileStoredItem(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type2 = this.getOutputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped(
            "Fix AbstractArrow item type",
            type,
            type2,
            ExtraDataFixUtils.chainAllFilters(
                this.fixChoice("minecraft:trident", FixProjectileStoredItem::castUnchecked),
                this.fixChoice("minecraft:arrow", FixProjectileStoredItem::fixArrow),
                this.fixChoice("minecraft:spectral_arrow", FixProjectileStoredItem::fixSpectralArrow)
            )
        );
    }

    private Function<Typed<?>, Typed<?>> fixChoice(String id, FixProjectileStoredItem.SubFixer<?> fixer) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, id);
        Type<?> type2 = this.getOutputSchema().getChoiceType(References.ENTITY, id);
        return fixChoiceCap(id, fixer, type, type2);
    }

    private static <T> Function<Typed<?>, Typed<?>> fixChoiceCap(String id, FixProjectileStoredItem.SubFixer<?> fixer, Type<?> inputType, Type<T> outputType) {
        OpticFinder<?> opticFinder = DSL.namedChoice(id, inputType);
        return typed -> typed.updateTyped(opticFinder, outputType, typedx -> fixer.fix(typedx, outputType));
    }

    private static <T> Typed<T> fixArrow(Typed<?> typed, Type<T> type) {
        return Util.writeAndReadTypedOrThrow(typed, type, data -> data.set("item", createItemStack(data, getArrowType(data))));
    }

    private static String getArrowType(Dynamic<?> arrowData) {
        return arrowData.get("Potion").asString("minecraft:empty").equals("minecraft:empty") ? "minecraft:arrow" : "minecraft:tipped_arrow";
    }

    private static <T> Typed<T> fixSpectralArrow(Typed<?> typed, Type<T> type) {
        return Util.writeAndReadTypedOrThrow(typed, type, data -> data.set("item", createItemStack(data, "minecraft:spectral_arrow")));
    }

    private static Dynamic<?> createItemStack(Dynamic<?> projectileData, String id) {
        return projectileData.createMap(
            ImmutableMap.of(
                projectileData.createString("id"), projectileData.createString(id), projectileData.createString("Count"), projectileData.createInt(1)
            )
        );
    }

    private static <T> Typed<T> castUnchecked(Typed<?> typed, Type<T> type) {
        return new Typed<>(type, typed.getOps(), (T)typed.getValue());
    }

    interface SubFixer<F> {
        Typed<F> fix(Typed<?> typed, Type<F> type);
    }
}
