package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

public class RandomPos {
    private static final int RANDOM_POS_ATTEMPTS = 10;

    public static BlockPos generateRandomDirection(RandomSource random, int horizontalRange, int verticalRange) {
        int i = random.nextInt(2 * horizontalRange + 1) - horizontalRange;
        int j = random.nextInt(2 * verticalRange + 1) - verticalRange;
        int k = random.nextInt(2 * horizontalRange + 1) - horizontalRange;
        return new BlockPos(i, j, k);
    }

    @Nullable
    public static BlockPos generateRandomDirectionWithinRadians(
        RandomSource random, int horizontalRange, int verticalRange, int startHeight, double directionX, double directionZ, double angleRange
    ) {
        double d = Mth.atan2(directionZ, directionX) - (float) (Math.PI / 2);
        double e = d + (double)(2.0F * random.nextFloat() - 1.0F) * angleRange;
        double f = Math.sqrt(random.nextDouble()) * (double)Mth.SQRT_OF_TWO * (double)horizontalRange;
        double g = -f * Math.sin(e);
        double h = f * Math.cos(e);
        if (!(Math.abs(g) > (double)horizontalRange) && !(Math.abs(h) > (double)horizontalRange)) {
            int i = random.nextInt(2 * verticalRange + 1) - verticalRange + startHeight;
            return BlockPos.containing(g, (double)i, h);
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpOutOfSolid(BlockPos pos, int maxY, Predicate<BlockPos> condition) {
        if (!condition.test(pos)) {
            return pos;
        } else {
            BlockPos blockPos = pos.above();

            while (blockPos.getY() < maxY && condition.test(blockPos)) {
                blockPos = blockPos.above();
            }

            return blockPos;
        }
    }

    @VisibleForTesting
    public static BlockPos moveUpToAboveSolid(BlockPos pos, int extraAbove, int max, Predicate<BlockPos> condition) {
        if (extraAbove < 0) {
            throw new IllegalArgumentException("aboveSolidAmount was " + extraAbove + ", expected >= 0");
        } else if (!condition.test(pos)) {
            return pos;
        } else {
            BlockPos blockPos = pos.above();

            while (blockPos.getY() < max && condition.test(blockPos)) {
                blockPos = blockPos.above();
            }

            BlockPos blockPos2 = blockPos;

            while (blockPos2.getY() < max && blockPos2.getY() - blockPos.getY() < extraAbove) {
                BlockPos blockPos3 = blockPos2.above();
                if (condition.test(blockPos3)) {
                    break;
                }

                blockPos2 = blockPos3;
            }

            return blockPos2;
        }
    }

    @Nullable
    public static Vec3 generateRandomPos(PathfinderMob entity, Supplier<BlockPos> factory) {
        return generateRandomPos(factory, entity::getWalkTargetValue);
    }

    @Nullable
    public static Vec3 generateRandomPos(Supplier<BlockPos> factory, ToDoubleFunction<BlockPos> scorer) {
        double d = Double.NEGATIVE_INFINITY;
        BlockPos blockPos = null;

        for (int i = 0; i < 10; i++) {
            BlockPos blockPos2 = factory.get();
            if (blockPos2 != null) {
                double e = scorer.applyAsDouble(blockPos2);
                if (e > d) {
                    d = e;
                    blockPos = blockPos2;
                }
            }
        }

        return blockPos != null ? Vec3.atBottomCenterOf(blockPos) : null;
    }

    public static BlockPos generateRandomPosTowardDirection(PathfinderMob entity, int horizontalRange, RandomSource random, BlockPos fuzz) {
        int i = fuzz.getX();
        int j = fuzz.getZ();
        if (entity.hasRestriction() && horizontalRange > 1) {
            BlockPos blockPos = entity.getRestrictCenter();
            if (entity.getX() > (double)blockPos.getX()) {
                i -= random.nextInt(horizontalRange / 2);
            } else {
                i += random.nextInt(horizontalRange / 2);
            }

            if (entity.getZ() > (double)blockPos.getZ()) {
                j -= random.nextInt(horizontalRange / 2);
            } else {
                j += random.nextInt(horizontalRange / 2);
            }
        }

        return BlockPos.containing((double)i + entity.getX(), (double)fuzz.getY() + entity.getY(), (double)j + entity.getZ());
    }
}
