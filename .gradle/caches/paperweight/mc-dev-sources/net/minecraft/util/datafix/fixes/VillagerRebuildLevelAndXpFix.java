package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.Mth;

public class VillagerRebuildLevelAndXpFix extends DataFix {
    private static final int TRADES_PER_LEVEL = 2;
    private static final int[] LEVEL_XP_THRESHOLDS = new int[]{0, 10, 50, 100, 150};

    public static int getMinXpPerLevel(int level) {
        return LEVEL_XP_THRESHOLDS[Mth.clamp(level - 1, 0, LEVEL_XP_THRESHOLDS.length - 1)];
    }

    public VillagerRebuildLevelAndXpFix(Schema outputSchema, boolean changesTyped) {
        super(outputSchema, changesTyped);
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, "minecraft:villager");
        OpticFinder<?> opticFinder = DSL.namedChoice("minecraft:villager", type);
        OpticFinder<?> opticFinder2 = type.findField("Offers");
        Type<?> type2 = opticFinder2.type();
        OpticFinder<?> opticFinder3 = type2.findField("Recipes");
        ListType<?> listType = (ListType<?>)opticFinder3.type();
        OpticFinder<?> opticFinder4 = listType.getElement().finder();
        return this.fixTypeEverywhereTyped(
            "Villager level and xp rebuild",
            this.getInputSchema().getType(References.ENTITY),
            entityTyped -> entityTyped.updateTyped(
                    opticFinder,
                    type,
                    villagerTyped -> {
                        Dynamic<?> dynamic = villagerTyped.get(DSL.remainderFinder());
                        int i = dynamic.get("VillagerData").get("level").asInt(0);
                        Typed<?> typed = villagerTyped;
                        if (i == 0 || i == 1) {
                            int j = villagerTyped.getOptionalTyped(opticFinder2)
                                .flatMap(offersTyped -> offersTyped.getOptionalTyped(opticFinder3))
                                .map(recipesTyped -> recipesTyped.getAllTyped(opticFinder4).size())
                                .orElse(0);
                            i = Mth.clamp(j / 2, 1, 5);
                            if (i > 1) {
                                typed = addLevel(villagerTyped, i);
                            }
                        }

                        Optional<Number> optional = dynamic.get("Xp").asNumber().result();
                        if (optional.isEmpty()) {
                            typed = addXpFromLevel(typed, i);
                        }

                        return typed;
                    }
                )
        );
    }

    private static Typed<?> addLevel(Typed<?> villagerTyped, int level) {
        return villagerTyped.update(
            DSL.remainderFinder(),
            villagerdynamic -> villagerdynamic.update(
                    "VillagerData", villagerDataDynamic -> villagerDataDynamic.set("level", villagerDataDynamic.createInt(level))
                )
        );
    }

    private static Typed<?> addXpFromLevel(Typed<?> villagerTyped, int level) {
        int i = getMinXpPerLevel(level);
        return villagerTyped.update(DSL.remainderFinder(), villagerDynamic -> villagerDynamic.set("Xp", villagerDynamic.createInt(i)));
    }
}
