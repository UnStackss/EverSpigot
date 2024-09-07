package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import net.minecraft.nbt.NbtFormatException;

public class WorldGenSettingsDisallowOldCustomWorldsFix extends DataFix {
    public WorldGenSettingsDisallowOldCustomWorldsFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.WORLD_GEN_SETTINGS);
        OpticFinder<?> opticFinder = type.findField("dimensions");
        return this.fixTypeEverywhereTyped(
            "WorldGenSettingsDisallowOldCustomWorldsFix_" + this.getOutputSchema().getVersionKey(),
            type,
            worldGenSettingsTyped -> worldGenSettingsTyped.updateTyped(opticFinder, dimensionsTyped -> {
                    dimensionsTyped.write().map(dimensionsDynamic -> dimensionsDynamic.getMapValues().map(dimensions -> {
                            dimensions.forEach((dimensionId, dimensionDynamic) -> {
                                if (dimensionDynamic.get("type").asString().result().isEmpty()) {
                                    throw new NbtFormatException("Unable load old custom worlds.");
                                }
                            });
                            return dimensions;
                        }));
                    return dimensionsTyped;
                })
        );
    }
}
