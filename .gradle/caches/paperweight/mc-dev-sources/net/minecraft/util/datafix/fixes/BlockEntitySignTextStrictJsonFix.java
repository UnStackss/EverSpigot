package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class BlockEntitySignTextStrictJsonFix extends NamedEntityFix {
    public BlockEntitySignTextStrictJsonFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntitySignTextStrictJsonFix", References.BLOCK_ENTITY, "Sign");
    }

    private Dynamic<?> updateLine(Dynamic<?> signDynamic, String lineName) {
        return signDynamic.update(lineName, ComponentDataFixUtils::rewriteFromLenient);
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), linesDynamic -> {
            linesDynamic = this.updateLine(linesDynamic, "Text1");
            linesDynamic = this.updateLine(linesDynamic, "Text2");
            linesDynamic = this.updateLine(linesDynamic, "Text3");
            return this.updateLine(linesDynamic, "Text4");
        });
    }
}
