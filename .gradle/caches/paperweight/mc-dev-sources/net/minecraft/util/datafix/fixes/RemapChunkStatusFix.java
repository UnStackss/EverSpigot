package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemapChunkStatusFix extends DataFix {
    private final String name;
    private final UnaryOperator<String> mapper;

    public RemapChunkStatusFix(Schema outputSchema, String name, UnaryOperator<String> mapper) {
        super(outputSchema, false);
        this.name = name;
        this.mapper = mapper;
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.name,
            this.getInputSchema().getType(References.CHUNK),
            typed -> typed.update(
                    DSL.remainderFinder(),
                    chunk -> chunk.update("Status", this::fixStatus).update("below_zero_retrogen", dynamic -> dynamic.update("target_status", this::fixStatus))
                )
        );
    }

    private <T> Dynamic<T> fixStatus(Dynamic<T> status) {
        Optional<Dynamic<T>> optional = status.asString().result().map(NamespacedSchema::ensureNamespaced).map(this.mapper).map(status::createString);
        return DataFixUtils.orElse(optional, status);
    }
}
