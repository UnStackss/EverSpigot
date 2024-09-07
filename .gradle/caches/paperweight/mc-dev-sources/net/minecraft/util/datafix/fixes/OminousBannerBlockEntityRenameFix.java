package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class OminousBannerBlockEntityRenameFix extends NamedEntityFix {
    public OminousBannerBlockEntityRenameFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "OminousBannerBlockEntityRenameFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fixTag);
    }

    private Dynamic<?> fixTag(Dynamic<?> bannerDynamic) {
        Optional<String> optional = bannerDynamic.get("CustomName").asString().result();
        if (optional.isPresent()) {
            String string = optional.get();
            string = string.replace("\"translate\":\"block.minecraft.illager_banner\"", "\"translate\":\"block.minecraft.ominous_banner\"");
            return bannerDynamic.set("CustomName", bannerDynamic.createString(string));
        } else {
            return bannerDynamic;
        }
    }
}
