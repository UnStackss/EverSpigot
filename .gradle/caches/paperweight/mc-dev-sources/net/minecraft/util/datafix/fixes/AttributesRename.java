package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.UnaryOperator;

public class AttributesRename extends DataFix {
    private final String name;
    private final UnaryOperator<String> renames;

    public AttributesRename(Schema outputSchema, String description, UnaryOperator<String> renames) {
        super(outputSchema, false);
        this.name = description;
        this.renames = renames;
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticFinder = type.findField("tag");
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(this.name + " (ItemStack)", type, itemStackTyped -> itemStackTyped.updateTyped(opticFinder, this::fixItemStackTag)),
            this.fixTypeEverywhereTyped(this.name + " (Entity)", this.getInputSchema().getType(References.ENTITY), this::fixEntity),
            this.fixTypeEverywhereTyped(this.name + " (Player)", this.getInputSchema().getType(References.PLAYER), this::fixEntity)
        );
    }

    private Dynamic<?> fixName(Dynamic<?> attributeNameDynamic) {
        return DataFixUtils.orElse(attributeNameDynamic.asString().result().map(this.renames).map(attributeNameDynamic::createString), attributeNameDynamic);
    }

    private Typed<?> fixItemStackTag(Typed<?> tagTyped) {
        return tagTyped.update(
            DSL.remainderFinder(),
            tagDynamic -> tagDynamic.update(
                    "AttributeModifiers",
                    attributeModifiersDynamic -> DataFixUtils.orElse(
                            attributeModifiersDynamic.asStreamOpt()
                                .result()
                                .map(
                                    attributeModifiers -> attributeModifiers.map(
                                            attributeModifierDynamic -> attributeModifierDynamic.update("AttributeName", this::fixName)
                                        )
                                )
                                .map(attributeModifiersDynamic::createList),
                            attributeModifiersDynamic
                        )
                )
        );
    }

    private Typed<?> fixEntity(Typed<?> entityTyped) {
        return entityTyped.update(
            DSL.remainderFinder(),
            entityDynamic -> entityDynamic.update(
                    "Attributes",
                    attributesDynamic -> DataFixUtils.orElse(
                            attributesDynamic.asStreamOpt()
                                .result()
                                .map(attributes -> attributes.map(attributeDynamic -> attributeDynamic.update("Name", this::fixName)))
                                .map(attributesDynamic::createList),
                            attributesDynamic
                        )
                )
        );
    }
}
