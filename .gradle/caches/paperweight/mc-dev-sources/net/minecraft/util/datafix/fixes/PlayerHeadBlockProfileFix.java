package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class PlayerHeadBlockProfileFix extends NamedEntityFix {
    public PlayerHeadBlockProfileFix(Schema outputSchema) {
        super(outputSchema, false, "PlayerHeadBlockProfileFix", References.BLOCK_ENTITY, "minecraft:skull");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> dynamic) {
        Optional<Dynamic<T>> optional = dynamic.get("SkullOwner").result();
        Optional<Dynamic<T>> optional2 = dynamic.get("ExtraType").result();
        Optional<Dynamic<T>> optional3 = optional.or(() -> optional2);
        if (optional3.isEmpty()) {
            return dynamic;
        } else {
            dynamic = dynamic.remove("SkullOwner").remove("ExtraType");
            return dynamic.set("profile", ItemStackComponentizationFix.fixProfile(optional3.get()));
        }
    }
}
