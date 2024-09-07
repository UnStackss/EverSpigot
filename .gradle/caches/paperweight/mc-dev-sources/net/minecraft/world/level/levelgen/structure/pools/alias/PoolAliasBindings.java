package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class PoolAliasBindings {
    public static MapCodec<? extends PoolAliasBinding> bootstrap(Registry<MapCodec<? extends PoolAliasBinding>> registry) {
        Registry.register(registry, "random", Random.CODEC);
        Registry.register(registry, "random_group", RandomGroup.CODEC);
        return Registry.register(registry, "direct", Direct.CODEC);
    }

    public static void registerTargetsAsPools(BootstrapContext<StructureTemplatePool> pools, Holder<StructureTemplatePool> base, List<PoolAliasBinding> aliases) {
        aliases.stream()
            .flatMap(PoolAliasBinding::allTargets)
            .map(target -> target.location().getPath())
            .forEach(
                path -> Pools.register(
                        pools,
                        path,
                        new StructureTemplatePool(base, List.of(Pair.of(StructurePoolElement.single(path), 1)), StructureTemplatePool.Projection.RIGID)
                    )
            );
    }
}
