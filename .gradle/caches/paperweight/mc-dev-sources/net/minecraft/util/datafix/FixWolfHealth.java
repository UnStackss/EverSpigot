package net.minecraft.util.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.datafix.fixes.NamedEntityFix;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class FixWolfHealth extends NamedEntityFix {
    private static final String WOLF_ID = "minecraft:wolf";
    private static final String WOLF_HEALTH = "minecraft:generic.max_health";

    public FixWolfHealth(Schema outputSchema) {
        super(outputSchema, false, "FixWolfHealth", References.ENTITY, "minecraft:wolf");
    }

    @Override
    protected Typed<?> fix(Typed<?> inputTyped) {
        return inputTyped.update(
            DSL.remainderFinder(),
            wolfDynamic -> {
                MutableBoolean mutableBoolean = new MutableBoolean(false);
                wolfDynamic = wolfDynamic.update(
                    "Attributes",
                    attributesDynamic -> attributesDynamic.createList(
                            attributesDynamic.asStream()
                                .map(
                                    attributeDynamic -> "minecraft:generic.max_health"
                                                .equals(NamespacedSchema.ensureNamespaced(attributeDynamic.get("Name").asString("")))
                                            ? attributeDynamic.update("Base", baseDynamic -> {
                                                if (baseDynamic.asDouble(0.0) == 20.0) {
                                                    mutableBoolean.setTrue();
                                                    return baseDynamic.createDouble(40.0);
                                                } else {
                                                    return baseDynamic;
                                                }
                                            })
                                            : attributeDynamic
                                )
                        )
                );
                if (mutableBoolean.isTrue()) {
                    wolfDynamic = wolfDynamic.update("Health", healthDynamic -> healthDynamic.createFloat(healthDynamic.asFloat(0.0F) * 2.0F));
                }

                return wolfDynamic;
            }
        );
    }
}
