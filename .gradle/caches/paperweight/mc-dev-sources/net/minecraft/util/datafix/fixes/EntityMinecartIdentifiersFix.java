package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import net.minecraft.Util;

public class EntityMinecartIdentifiersFix extends EntityRenameFix {
    public EntityMinecartIdentifiersFix(Schema outputSchema) {
        super("EntityMinecartIdentifiersFix", outputSchema, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String choice, Typed<?> entityTyped) {
        if (!choice.equals("Minecart")) {
            return Pair.of(choice, entityTyped);
        } else {
            int i = entityTyped.getOrCreate(DSL.remainderFinder()).get("Type").asInt(0);

            String string = switch (i) {
                case 1 -> "MinecartChest";
                case 2 -> "MinecartFurnace";
                default -> "MinecartRideable";
            };
            Type<?> type = this.getOutputSchema().findChoiceType(References.ENTITY).types().get(string);
            return Pair.of(string, Util.writeAndReadTypedOrThrow(entityTyped, type, entityDynamic -> entityDynamic.remove("Type")));
        }
    }
}
