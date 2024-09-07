package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public abstract class ItemStackComponentRemainderFix extends DataFix {
    private final String name;
    private final String componentId;
    private final String newComponentId;

    public ItemStackComponentRemainderFix(Schema outputSchema, String name, String componentId) {
        this(outputSchema, name, componentId, componentId);
    }

    public ItemStackComponentRemainderFix(Schema outputSchema, String name, String oldComponentId, String newComponentId) {
        super(outputSchema, false);
        this.name = name;
        this.componentId = oldComponentId;
        this.newComponentId = newComponentId;
    }

    public final TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("components");
        return this.fixTypeEverywhereTyped(
            this.name,
            type,
            typed -> typed.updateTyped(
                    opticFinder,
                    typedx -> typedx.update(
                            DSL.remainderFinder(), dynamic -> dynamic.renameAndFixField(this.componentId, this.newComponentId, this::fixComponent)
                        )
                )
        );
    }

    protected abstract <T> Dynamic<T> fixComponent(Dynamic<T> dynamic);
}
