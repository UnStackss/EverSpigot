package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class FilteredSignsFix extends NamedEntityFix {
    public FilteredSignsFix(Schema outputSchema) {
        super(outputSchema, false, "Remove filtered text from signs", References.BLOCK_ENTITY, "minecraft:sign");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(
            DSL.remainderFinder(), blockEntity -> blockEntity.remove("FilteredText1").remove("FilteredText2").remove("FilteredText3").remove("FilteredText4")
        );
    }
}
