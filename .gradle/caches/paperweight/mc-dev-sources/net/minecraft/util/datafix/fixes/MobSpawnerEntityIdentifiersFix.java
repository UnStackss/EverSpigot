package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.stream.Stream;

public class MobSpawnerEntityIdentifiersFix extends DataFix {
    public MobSpawnerEntityIdentifiersFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private Dynamic<?> fix(Dynamic<?> spawnerDynamic) {
        if (!"MobSpawner".equals(spawnerDynamic.get("id").asString(""))) {
            return spawnerDynamic;
        } else {
            Optional<String> optional = spawnerDynamic.get("EntityId").asString().result();
            if (optional.isPresent()) {
                Dynamic<?> dynamic = DataFixUtils.orElse(spawnerDynamic.get("SpawnData").result(), spawnerDynamic.emptyMap());
                dynamic = dynamic.set("id", dynamic.createString(optional.get().isEmpty() ? "Pig" : optional.get()));
                spawnerDynamic = spawnerDynamic.set("SpawnData", dynamic);
                spawnerDynamic = spawnerDynamic.remove("EntityId");
            }

            Optional<? extends Stream<? extends Dynamic<?>>> optional2 = spawnerDynamic.get("SpawnPotentials").asStreamOpt().result();
            if (optional2.isPresent()) {
                spawnerDynamic = spawnerDynamic.set(
                    "SpawnPotentials",
                    spawnerDynamic.createList(
                        optional2.get()
                            .map(
                                spawnPotentialsDynamic -> {
                                    Optional<String> optionalx = spawnPotentialsDynamic.get("Type").asString().result();
                                    if (optionalx.isPresent()) {
                                        Dynamic<?> dynamic = DataFixUtils.orElse(
                                                spawnPotentialsDynamic.get("Properties").result(), spawnPotentialsDynamic.emptyMap()
                                            )
                                            .set("id", spawnPotentialsDynamic.createString(optionalx.get()));
                                        return spawnPotentialsDynamic.set("Entity", dynamic).remove("Type").remove("Properties");
                                    } else {
                                        return spawnPotentialsDynamic;
                                    }
                                }
                            )
                    )
                );
            }

            return spawnerDynamic;
        }
    }

    public TypeRewriteRule makeRule() {
        Type<?> type = this.getOutputSchema().getType(References.UNTAGGED_SPAWNER);
        return this.fixTypeEverywhereTyped(
            "MobSpawnerEntityIdentifiersFix", this.getInputSchema().getType(References.UNTAGGED_SPAWNER), type, untaggedSpawnerTyped -> {
                Dynamic<?> dynamic = untaggedSpawnerTyped.get(DSL.remainderFinder());
                dynamic = dynamic.set("id", dynamic.createString("MobSpawner"));
                DataResult<? extends Pair<? extends Typed<?>, ?>> dataResult = type.readTyped(this.fix(dynamic));
                return dataResult.result().isEmpty() ? untaggedSpawnerTyped : dataResult.result().get().getFirst();
            }
        );
    }
}
