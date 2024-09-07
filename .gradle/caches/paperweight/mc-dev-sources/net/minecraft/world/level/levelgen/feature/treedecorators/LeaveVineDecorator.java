package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class LeaveVineDecorator extends TreeDecorator {
    public static final MapCodec<LeaveVineDecorator> CODEC = Codec.floatRange(0.0F, 1.0F)
        .fieldOf("probability")
        .xmap(LeaveVineDecorator::new, treeDecorator -> treeDecorator.probability);
    private final float probability;

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.LEAVE_VINE;
    }

    public LeaveVineDecorator(float probability) {
        this.probability = probability;
    }

    @Override
    public void place(TreeDecorator.Context generator) {
        RandomSource randomSource = generator.random();
        generator.leaves().forEach(pos -> {
            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos = pos.west();
                if (generator.isAir(blockPos)) {
                    addHangingVine(blockPos, VineBlock.EAST, generator);
                }
            }

            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos2 = pos.east();
                if (generator.isAir(blockPos2)) {
                    addHangingVine(blockPos2, VineBlock.WEST, generator);
                }
            }

            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos3 = pos.north();
                if (generator.isAir(blockPos3)) {
                    addHangingVine(blockPos3, VineBlock.SOUTH, generator);
                }
            }

            if (randomSource.nextFloat() < this.probability) {
                BlockPos blockPos4 = pos.south();
                if (generator.isAir(blockPos4)) {
                    addHangingVine(blockPos4, VineBlock.NORTH, generator);
                }
            }
        });
    }

    private static void addHangingVine(BlockPos pos, BooleanProperty faceProperty, TreeDecorator.Context generator) {
        generator.placeVine(pos, faceProperty);
        int i = 4;

        for (BlockPos var4 = pos.below(); generator.isAir(var4) && i > 0; i--) {
            generator.placeVine(var4, faceProperty);
            var4 = var4.below();
        }
    }
}
