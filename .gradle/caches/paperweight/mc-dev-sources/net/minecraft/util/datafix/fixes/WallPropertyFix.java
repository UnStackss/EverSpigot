package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Set;

public class WallPropertyFix extends DataFix {
    private static final Set<String> WALL_BLOCKS = ImmutableSet.of(
        "minecraft:andesite_wall",
        "minecraft:brick_wall",
        "minecraft:cobblestone_wall",
        "minecraft:diorite_wall",
        "minecraft:end_stone_brick_wall",
        "minecraft:granite_wall",
        "minecraft:mossy_cobblestone_wall",
        "minecraft:mossy_stone_brick_wall",
        "minecraft:nether_brick_wall",
        "minecraft:prismarine_wall",
        "minecraft:red_nether_brick_wall",
        "minecraft:red_sandstone_wall",
        "minecraft:sandstone_wall",
        "minecraft:stone_brick_wall"
    );

    public WallPropertyFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "WallPropertyFix",
            this.getInputSchema().getType(References.BLOCK_STATE),
            blockStateTyped -> blockStateTyped.update(DSL.remainderFinder(), WallPropertyFix::upgradeBlockStateTag)
        );
    }

    private static String mapProperty(String value) {
        return "true".equals(value) ? "low" : "none";
    }

    private static <T> Dynamic<T> fixWallProperty(Dynamic<T> propertiesDynamic, String propertyName) {
        return propertiesDynamic.update(
            propertyName,
            propertyValue -> DataFixUtils.orElse(
                    propertyValue.asString().result().map(WallPropertyFix::mapProperty).map(propertyValue::createString), propertyValue
                )
        );
    }

    private static <T> Dynamic<T> upgradeBlockStateTag(Dynamic<T> blockStateDynamic) {
        boolean bl = blockStateDynamic.get("Name").asString().result().filter(WALL_BLOCKS::contains).isPresent();
        return !bl ? blockStateDynamic : blockStateDynamic.update("Properties", propertiesDynamic -> {
            Dynamic<?> dynamic = fixWallProperty(propertiesDynamic, "east");
            dynamic = fixWallProperty(dynamic, "west");
            dynamic = fixWallProperty(dynamic, "north");
            return fixWallProperty(dynamic, "south");
        });
    }
}
