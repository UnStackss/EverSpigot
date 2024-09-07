package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.List;

public class SpawnerDataFix extends DataFix {
    public SpawnerDataFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.UNTAGGED_SPAWNER);
        Type<?> type2 = this.getOutputSchema().getType(References.UNTAGGED_SPAWNER);
        OpticFinder<?> opticFinder = type.findField("SpawnData");
        Type<?> type3 = type2.findField("SpawnData").type();
        OpticFinder<?> opticFinder2 = type.findField("SpawnPotentials");
        Type<?> type4 = type2.findField("SpawnPotentials").type();
        return this.fixTypeEverywhereTyped(
            "Fix mob spawner data structure",
            type,
            type2,
            untaggedSpawnerTyped -> untaggedSpawnerTyped.updateTyped(opticFinder, type3, spawnDataTyped -> this.wrapEntityToSpawnData(type3, spawnDataTyped))
                    .updateTyped(opticFinder2, type4, spawnPotentialsTyped -> this.wrapSpawnPotentialsToWeightedEntries(type4, spawnPotentialsTyped))
        );
    }

    private <T> Typed<T> wrapEntityToSpawnData(Type<T> spawnDataType, Typed<?> spawnDataTyped) {
        DynamicOps<?> dynamicOps = spawnDataTyped.getOps();
        return new Typed<>(spawnDataType, dynamicOps, (T)Pair.<Object, Dynamic<?>>of(spawnDataTyped.getValue(), new Dynamic<>(dynamicOps)));
    }

    private <T> Typed<T> wrapSpawnPotentialsToWeightedEntries(Type<T> spawnPotentialsType, Typed<?> spawnPotentialsTyped) {
        DynamicOps<?> dynamicOps = spawnPotentialsTyped.getOps();
        List<?> list = (List<?>)spawnPotentialsTyped.getValue();
        List<?> list2 = list.stream().map(object -> {
            Pair<Object, Dynamic<?>> pair = (Pair<Object, Dynamic<?>>)object;
            int i = pair.getSecond().get("Weight").asNumber().result().orElse(1).intValue();
            Dynamic<?> dynamic = new Dynamic<>(dynamicOps);
            dynamic = dynamic.set("weight", dynamic.createInt(i));
            Dynamic<?> dynamic2 = pair.getSecond().remove("Weight").remove("Entity");
            return Pair.of(Pair.of(pair.getFirst(), dynamic2), dynamic);
        }).toList();
        return new Typed<>(spawnPotentialsType, dynamicOps, (T)list2);
    }
}
