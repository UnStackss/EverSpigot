package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import java.util.function.Function;

public class ChunkRenamesFix extends DataFix {
    public ChunkRenamesFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Structures");
        Type<?> type2 = this.getOutputSchema().getType(References.CHUNK);
        Type<?> type3 = type2.findFieldType("structures");
        return this.fixTypeEverywhereTyped("Chunk Renames; purge Level-tag", type, type2, chunkTyped -> {
            Typed<?> typed = chunkTyped.getTyped(opticFinder);
            Typed<?> typed2 = appendChunkName(typed);
            typed2 = typed2.set(DSL.remainderFinder(), mergeRemainders(chunkTyped, typed.get(DSL.remainderFinder())));
            typed2 = renameField(typed2, "TileEntities", "block_entities");
            typed2 = renameField(typed2, "TileTicks", "block_ticks");
            typed2 = renameField(typed2, "Entities", "entities");
            typed2 = renameField(typed2, "Sections", "sections");
            typed2 = typed2.updateTyped(opticFinder2, type3, structuresTyped -> renameField(structuresTyped, "Starts", "starts"));
            typed2 = renameField(typed2, "Structures", "structures");
            return typed2.update(DSL.remainderFinder(), dynamic -> dynamic.remove("Level"));
        });
    }

    private static Typed<?> renameField(Typed<?> typed, String oldKey, String newKey) {
        return renameFieldHelper(typed, oldKey, newKey, typed.getType().findFieldType(oldKey)).update(DSL.remainderFinder(), dynamic -> dynamic.remove(oldKey));
    }

    private static <A> Typed<?> renameFieldHelper(Typed<?> typed, String oldKey, String newKey, Type<A> type) {
        Type<Either<A, Unit>> type2 = DSL.optional(DSL.field(oldKey, type));
        Type<Either<A, Unit>> type3 = DSL.optional(DSL.field(newKey, type));
        return typed.update(type2.finder(), type3, Function.identity());
    }

    private static <A> Typed<Pair<String, A>> appendChunkName(Typed<A> outputTyped) {
        return new Typed<>(DSL.named("chunk", outputTyped.getType()), outputTyped.getOps(), Pair.of("chunk", outputTyped.getValue()));
    }

    private static <T> Dynamic<T> mergeRemainders(Typed<?> chunkTyped, Dynamic<T> chunkDynamic) {
        DynamicOps<T> dynamicOps = chunkDynamic.getOps();
        Dynamic<T> dynamic = chunkTyped.get(DSL.remainderFinder()).convert(dynamicOps);
        DataResult<T> dataResult = dynamicOps.getMap(chunkDynamic.getValue())
            .flatMap(mapLike -> dynamicOps.mergeToMap(dynamic.getValue(), (MapLike<T>)mapLike));
        return dataResult.result().map(object -> new Dynamic<>(dynamicOps, (T)object)).orElse(chunkDynamic);
    }
}
