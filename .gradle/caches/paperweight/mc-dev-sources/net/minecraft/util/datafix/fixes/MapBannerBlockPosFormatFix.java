package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class MapBannerBlockPosFormatFix extends DataFix {
    public MapBannerBlockPosFormatFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Dynamic<T> fixMapSavedData(Dynamic<T> mapDataDynamic) {
        return mapDataDynamic.update(
            "banners", banners -> banners.createList(banners.asStream().map(banner -> banner.update("Pos", ExtraDataFixUtils::fixBlockPos)))
        );
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "MapBannerBlockPosFormatFix",
            this.getInputSchema().getType(References.SAVED_DATA_MAP_DATA),
            mapDatTyped -> mapDatTyped.update(DSL.remainderFinder(), mapDatDynamic -> mapDatDynamic.update("data", MapBannerBlockPosFormatFix::fixMapSavedData))
        );
    }
}
