package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public interface PositionalRandomFactory {
    default RandomSource at(BlockPos pos) {
        return this.at(pos.getX(), pos.getY(), pos.getZ());
    }

    default RandomSource fromHashOf(ResourceLocation seed) {
        return this.fromHashOf(seed.toString());
    }

    RandomSource fromHashOf(String seed);

    RandomSource fromSeed(long seed);

    RandomSource at(int x, int y, int z);

    @VisibleForTesting
    void parityConfigString(StringBuilder info);
}
