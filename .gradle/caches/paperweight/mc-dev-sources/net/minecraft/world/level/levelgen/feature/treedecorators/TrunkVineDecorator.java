package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;

public class TrunkVineDecorator extends TreeDecorator {
    public static final MapCodec<TrunkVineDecorator> CODEC = MapCodec.unit(() -> TrunkVineDecorator.INSTANCE);
    public static final TrunkVineDecorator INSTANCE = new TrunkVineDecorator();

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.TRUNK_VINE;
    }

    @Override
    public void place(TreeDecorator.Context generator) {
        RandomSource randomSource = generator.random();
        generator.logs().forEach(pos -> {
            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos = pos.west();
                if (generator.isAir(blockPos)) {
                    generator.placeVine(blockPos, VineBlock.EAST);
                }
            }

            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos2 = pos.east();
                if (generator.isAir(blockPos2)) {
                    generator.placeVine(blockPos2, VineBlock.WEST);
                }
            }

            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos3 = pos.north();
                if (generator.isAir(blockPos3)) {
                    generator.placeVine(blockPos3, VineBlock.SOUTH);
                }
            }

            if (randomSource.nextInt(3) > 0) {
                BlockPos blockPos4 = pos.south();
                if (generator.isAir(blockPos4)) {
                    generator.placeVine(blockPos4, VineBlock.NORTH);
                }
            }
        });
    }
}
