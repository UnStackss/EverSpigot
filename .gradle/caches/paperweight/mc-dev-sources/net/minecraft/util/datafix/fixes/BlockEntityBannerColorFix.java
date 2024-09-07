package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityBannerColorFix extends NamedEntityFix {
    public BlockEntityBannerColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityBannerColorFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    public Dynamic<?> fixTag(Dynamic<?> bannerDynamic) {
        bannerDynamic = bannerDynamic.update("Base", baseDynamic -> baseDynamic.createInt(15 - baseDynamic.asInt(0)));
        return bannerDynamic.update(
            "Patterns",
            patternsDynamic -> DataFixUtils.orElse(
                    patternsDynamic.asStreamOpt()
                        .map(
                            stream -> stream.map(
                                    patternDynamic -> patternDynamic.update("Color", colorDynamic -> colorDynamic.createInt(15 - colorDynamic.asInt(0)))
                                )
                        )
                        .map(patternsDynamic::createList)
                        .result(),
                    patternsDynamic
                )
        );
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }
}
