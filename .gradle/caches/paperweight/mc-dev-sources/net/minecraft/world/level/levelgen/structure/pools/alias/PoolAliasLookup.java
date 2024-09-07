package net.minecraft.world.level.levelgen.structure.pools.alias;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

@FunctionalInterface
public interface PoolAliasLookup {
    PoolAliasLookup EMPTY = pool -> pool;

    ResourceKey<StructureTemplatePool> lookup(ResourceKey<StructureTemplatePool> pool);

    static PoolAliasLookup create(List<PoolAliasBinding> bindings, BlockPos pos, long seed) {
        if (bindings.isEmpty()) {
            return EMPTY;
        } else {
            RandomSource randomSource = RandomSource.create(seed).forkPositional().at(pos);
            Builder<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> builder = ImmutableMap.builder();
            bindings.forEach(binding -> binding.forEachResolved(randomSource, builder::put));
            Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = builder.build();
            return alias -> Objects.requireNonNull(map.getOrDefault(alias, alias), () -> "alias " + alias + " was mapped to null value");
        }
    }
}
