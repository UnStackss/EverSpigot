package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Map;
import java.util.Optional;

public class PrimedTntBlockStateFixer extends NamedEntityWriteReadFix {
    public PrimedTntBlockStateFixer(Schema outputSchema) {
        super(outputSchema, true, "PrimedTnt BlockState fixer", References.ENTITY, "minecraft:tnt");
    }

    private static <T> Dynamic<T> renameFuse(Dynamic<T> data) {
        Optional<Dynamic<T>> optional = data.get("Fuse").get().result();
        return optional.isPresent() ? data.set("fuse", optional.get()) : data;
    }

    private static <T> Dynamic<T> insertBlockState(Dynamic<T> data) {
        return data.set("block_state", data.createMap(Map.of(data.createString("Name"), data.createString("minecraft:tnt"))));
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> data) {
        return renameFuse(insertBlockState(data));
    }
}
