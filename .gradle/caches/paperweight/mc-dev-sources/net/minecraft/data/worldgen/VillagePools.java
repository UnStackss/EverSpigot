package net.minecraft.data.worldgen;

import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class VillagePools {
    public static void bootstrap(BootstrapContext<StructureTemplatePool> poolRegisterable) {
        PlainVillagePools.bootstrap(poolRegisterable);
        SnowyVillagePools.bootstrap(poolRegisterable);
        SavannaVillagePools.bootstrap(poolRegisterable);
        DesertVillagePools.bootstrap(poolRegisterable);
        TaigaVillagePools.bootstrap(poolRegisterable);
    }
}
