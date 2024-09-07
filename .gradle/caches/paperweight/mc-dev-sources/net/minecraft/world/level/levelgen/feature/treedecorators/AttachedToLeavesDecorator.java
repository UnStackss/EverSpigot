package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLeavesDecorator extends TreeDecorator {
    public static final MapCodec<AttachedToLeavesDecorator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(treeDecorator -> treeDecorator.probability),
                    Codec.intRange(0, 16).fieldOf("exclusion_radius_xz").forGetter(treeDecorator -> treeDecorator.exclusionRadiusXZ),
                    Codec.intRange(0, 16).fieldOf("exclusion_radius_y").forGetter(treeDecorator -> treeDecorator.exclusionRadiusY),
                    BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(treeDecorator -> treeDecorator.blockProvider),
                    Codec.intRange(1, 16).fieldOf("required_empty_blocks").forGetter(treeDecorator -> treeDecorator.requiredEmptyBlocks),
                    ExtraCodecs.nonEmptyList(Direction.CODEC.listOf()).fieldOf("directions").forGetter(treeDecorator -> treeDecorator.directions)
                )
                .apply(instance, AttachedToLeavesDecorator::new)
    );
    protected final float probability;
    protected final int exclusionRadiusXZ;
    protected final int exclusionRadiusY;
    protected final BlockStateProvider blockProvider;
    protected final int requiredEmptyBlocks;
    protected final List<Direction> directions;

    public AttachedToLeavesDecorator(
        float probability, int exclusionRadiusXZ, int exclusionRadiusY, BlockStateProvider blockProvider, int requiredEmptyBlocks, List<Direction> directions
    ) {
        this.probability = probability;
        this.exclusionRadiusXZ = exclusionRadiusXZ;
        this.exclusionRadiusY = exclusionRadiusY;
        this.blockProvider = blockProvider;
        this.requiredEmptyBlocks = requiredEmptyBlocks;
        this.directions = directions;
    }

    @Override
    public void place(TreeDecorator.Context generator) {
        Set<BlockPos> set = new HashSet<>();
        RandomSource randomSource = generator.random();

        for (BlockPos blockPos : Util.shuffledCopy(generator.leaves(), randomSource)) {
            Direction direction = Util.getRandom(this.directions, randomSource);
            BlockPos blockPos2 = blockPos.relative(direction);
            if (!set.contains(blockPos2) && randomSource.nextFloat() < this.probability && this.hasRequiredEmptyBlocks(generator, blockPos, direction)) {
                BlockPos blockPos3 = blockPos2.offset(-this.exclusionRadiusXZ, -this.exclusionRadiusY, -this.exclusionRadiusXZ);
                BlockPos blockPos4 = blockPos2.offset(this.exclusionRadiusXZ, this.exclusionRadiusY, this.exclusionRadiusXZ);

                for (BlockPos blockPos5 : BlockPos.betweenClosed(blockPos3, blockPos4)) {
                    set.add(blockPos5.immutable());
                }

                generator.setBlock(blockPos2, this.blockProvider.getState(randomSource, blockPos2));
            }
        }
    }

    private boolean hasRequiredEmptyBlocks(TreeDecorator.Context generator, BlockPos pos, Direction direction) {
        for (int i = 1; i <= this.requiredEmptyBlocks; i++) {
            BlockPos blockPos = pos.relative(direction, i);
            if (!generator.isAir(blockPos)) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ATTACHED_TO_LEAVES;
    }
}
