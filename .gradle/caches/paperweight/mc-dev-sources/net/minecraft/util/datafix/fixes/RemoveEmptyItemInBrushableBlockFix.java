package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RemoveEmptyItemInBrushableBlockFix extends NamedEntityWriteReadFix {
    public RemoveEmptyItemInBrushableBlockFix(Schema outputSchema) {
        super(outputSchema, false, "RemoveEmptyItemInSuspiciousBlockFix", References.BLOCK_ENTITY, "minecraft:brushable_block");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> data) {
        Optional<Dynamic<T>> optional = data.get("item").result();
        return optional.isPresent() && isEmptyStack(optional.get()) ? data.remove("item") : data;
    }

    private static boolean isEmptyStack(Dynamic<?> dynamic) {
        String string = NamespacedSchema.ensureNamespaced(dynamic.get("id").asString("minecraft:air"));
        int i = dynamic.get("count").asInt(0);
        return string.equals("minecraft:air") || i == 0;
    }
}
