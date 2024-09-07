package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import org.slf4j.Logger;

public class LevelUUIDFix extends AbstractUUIDFix {
    private static final Logger LOGGER = LogUtils.getLogger();

    public LevelUUIDFix(Schema outputSchema) {
        super(outputSchema, References.LEVEL);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "LevelUUIDFix",
            this.getInputSchema().getType(this.typeReference),
            levelTyped -> levelTyped.updateTyped(DSL.remainderFinder(), levelTyped2 -> levelTyped2.update(DSL.remainderFinder(), levelDynamic -> {
                        levelDynamic = this.updateCustomBossEvents(levelDynamic);
                        levelDynamic = this.updateDragonFight(levelDynamic);
                        return this.updateWanderingTrader(levelDynamic);
                    }))
        );
    }

    private Dynamic<?> updateWanderingTrader(Dynamic<?> levelDynamic) {
        return replaceUUIDString(levelDynamic, "WanderingTraderId", "WanderingTraderId").orElse(levelDynamic);
    }

    private Dynamic<?> updateDragonFight(Dynamic<?> levelDynamic) {
        return levelDynamic.update(
            "DimensionData",
            dimensionDataDynamic -> dimensionDataDynamic.updateMapValues(
                    entry -> entry.mapSecond(
                            dimensionDataValueDynamic -> dimensionDataValueDynamic.update(
                                    "DragonFight",
                                    dragonFightDynamic -> replaceUUIDLeastMost(dragonFightDynamic, "DragonUUID", "Dragon").orElse(dragonFightDynamic)
                                )
                        )
                )
        );
    }

    private Dynamic<?> updateCustomBossEvents(Dynamic<?> levelDynamic) {
        return levelDynamic.update(
            "CustomBossEvents",
            bossbarsDynamic -> bossbarsDynamic.updateMapValues(
                    entry -> entry.mapSecond(
                            bossbarDynamic -> bossbarDynamic.update(
                                    "Players",
                                    playersDynamic -> bossbarDynamic.createList(
                                            playersDynamic.asStream().map(playerDynamic -> createUUIDFromML((Dynamic<?>)playerDynamic).orElseGet(() -> {
                                                    LOGGER.warn("CustomBossEvents contains invalid UUIDs.");
                                                    return playerDynamic;
                                                }))
                                        )
                                )
                        )
                )
        );
    }
}
