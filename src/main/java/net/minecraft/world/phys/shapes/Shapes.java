package net.minecraft.world.phys.shapes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.math.DoubleMath;
import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class Shapes {
    public static final double EPSILON = 1.0E-7;
    public static final double BIG_EPSILON = 1.0E-6;
    private static final VoxelShape BLOCK = Util.make(() -> {
        // Paper start - optimise collisions
        final DiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        shape.fill(0, 0, 0);

        return new ArrayVoxelShape(
            shape,
            ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.ZERO_ONE, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.ZERO_ONE, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.ZERO_ONE
        );
        // Paper end - optimise collisions
    });
    public static final VoxelShape INFINITY = box(
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.POSITIVE_INFINITY
    );
    private static final VoxelShape EMPTY = new ArrayVoxelShape(
        new BitSetDiscreteVoxelShape(0, 0, 0),
        new DoubleArrayList(new double[]{0.0}),
        new DoubleArrayList(new double[]{0.0}),
        new DoubleArrayList(new double[]{0.0})
    );

    public static VoxelShape empty() {
        return EMPTY;
    }

    public static VoxelShape block() {
        return BLOCK;
    }

    // Paper start - optimise collisions
    private static final DoubleArrayList[] PARTS_BY_BITS = new DoubleArrayList[] {
        DoubleArrayList.wrap(generateCubeParts(1 << 0)),
        DoubleArrayList.wrap(generateCubeParts(1 << 1)),
        DoubleArrayList.wrap(generateCubeParts(1 << 2)),
        DoubleArrayList.wrap(generateCubeParts(1 << 3))
    };

    private static double[] generateCubeParts(final int parts) {
        // note: parts is a power of two, so we do not need to worry about loss of precision here
        // note: parts is from [2^0, 2^3]
        final double inc = 1.0 / (double)parts;

        final double[] ret = new double[parts + 1];
        double val = 0.0;
        for (int i = 0; i <= parts; ++i) {
            ret[i] = val;
            val += inc;
        }

        return ret;
    }
    // Paper end - optimise collisions

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(minX > maxX) && !(minY > maxY) && !(minZ > maxZ)) {
            return create(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            throw new IllegalArgumentException("The min values need to be smaller or equals to the max values");
        }
    }

    public static VoxelShape create(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // Paper start - optimise collisions
        if (!(maxX - minX < 1.0E-7) && !(maxY - minY < 1.0E-7) && !(maxZ - minZ < 1.0E-7)) {
            final int bitsX = findBits(minX, maxX);
            final int bitsY = findBits(minY, maxY);
            final int bitsZ = findBits(minZ, maxZ);
            if (bitsX >= 0 && bitsY >= 0 && bitsZ >= 0) {
                if (bitsX == 0 && bitsY == 0 && bitsZ == 0) {
                    return BLOCK;
                } else {
                    final int sizeX = 1 << bitsX;
                    final int sizeY = 1 << bitsY;
                    final int sizeZ = 1 << bitsZ;
                    final BitSetDiscreteVoxelShape shape = BitSetDiscreteVoxelShape.withFilledBounds(
                        sizeX, sizeY, sizeZ,
                        (int)Math.round(minX * (double)sizeX), (int)Math.round(minY * (double)sizeY), (int)Math.round(minZ * (double)sizeZ),
                        (int)Math.round(maxX * (double)sizeX), (int)Math.round(maxY * (double)sizeY), (int)Math.round(maxZ * (double)sizeZ)
                    );
                    return new ArrayVoxelShape(
                        shape,
                        PARTS_BY_BITS[bitsX],
                        PARTS_BY_BITS[bitsY],
                        PARTS_BY_BITS[bitsZ]
                    );
                }
            } else {
                return new ArrayVoxelShape(
                    BLOCK.shape,
                    minX == 0.0 && maxX == 1.0 ? ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minX, maxX }),
                    minY == 0.0 && maxY == 1.0 ? ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minY, maxY }),
                    minZ == 0.0 && maxZ == 1.0 ? ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.ZERO_ONE : DoubleArrayList.wrap(new double[] { minZ, maxZ })
                );
            }
        } else {
            return EMPTY;
        }
        // Paper end - optimise collisions
    }

    public static VoxelShape create(AABB box) {
        return create(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @VisibleForTesting
    protected static int findBits(double min, double max) {
        if (!(min < -1.0E-7) && !(max > 1.0000001)) {
            for (int i = 0; i <= 3; i++) {
                int j = 1 << i;
                double d = min * (double)j;
                double e = max * (double)j;
                boolean bl = Math.abs(d - (double)Math.round(d)) < 1.0E-7 * (double)j;
                boolean bl2 = Math.abs(e - (double)Math.round(e)) < 1.0E-7 * (double)j;
                if (bl && bl2) {
                    return i;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    protected static long lcm(int a, int b) {
        return (long)a * (long)(b / IntMath.gcd(a, b));
    }

    public static VoxelShape or(VoxelShape first, VoxelShape second) {
        return join(first, second, BooleanOp.OR);
    }

    // Paper start - optimise collisions
    public static VoxelShape or(VoxelShape shape, VoxelShape... others) {
        int size = others.length;
        if (size == 0) {
            return shape;
        }

        // reduce complexity of joins by splitting the merges

        // add extra slot for first shape
        ++size;
        final VoxelShape[] tmp = Arrays.copyOf(others, size);
        // insert first shape
        tmp[size - 1] = shape;

        while (size > 1) {
            int newSize = 0;
            for (int i = 0; i < size; i += 2) {
                final int next = i + 1;
                if (next >= size) {
                    // nothing to merge with, so leave it for next iteration
                    tmp[newSize++] = tmp[i];
                    break;
                } else {
                    // merge with adjacent
                    final VoxelShape first = tmp[i];
                    final VoxelShape second = tmp[next];

                    tmp[newSize++] = Shapes.or(first, second);
                }
            }
            size = newSize;
        }

        return tmp[0];
        // Paper end - optimise collisions
    }

    public static VoxelShape join(VoxelShape first, VoxelShape second, BooleanOp function) {
        return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.joinOptimized(first, second, function); // Paper - optimise collisions
    }

    public static VoxelShape joinUnoptimized(VoxelShape one, VoxelShape two, BooleanOp function) {
        return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.joinUnoptimized(one, two, function); // Paper - optimise collisions
    }

    public static boolean joinIsNotEmpty(VoxelShape shape1, VoxelShape shape2, BooleanOp predicate) {
        return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.isJoinNonEmpty(shape1, shape2, predicate); // Paper - optimise collisions
    }

    private static boolean joinIsNotEmpty(
        IndexMerger mergedX, IndexMerger mergedY, IndexMerger mergedZ, DiscreteVoxelShape shape1, DiscreteVoxelShape shape2, BooleanOp predicate
    ) {
        return !mergedX.forMergedIndexes(
            (x1, x2, index1) -> mergedY.forMergedIndexes(
                    (y1, y2, index2) -> mergedZ.forMergedIndexes(
                            (z1, z2, index3) -> !predicate.apply(shape1.isFullWide(x1, y1, z1), shape2.isFullWide(x2, y2, z2))
                        )
                )
        );
    }

    public static double collide(Direction.Axis axis, AABB box, Iterable<VoxelShape> shapes, double maxDist) {
        for (VoxelShape voxelShape : shapes) {
            if (Math.abs(maxDist) < 1.0E-7) {
                return 0.0;
            }

            maxDist = voxelShape.collide(axis, box, maxDist);
        }

        return maxDist;
    }

    // Paper start - optimise collisions
    public static boolean blockOccudes(final VoxelShape first, final VoxelShape second, final Direction direction) {
        final boolean firstBlock = first == BLOCK;
        final boolean secondBlock = second == BLOCK;

        if (firstBlock & secondBlock) {
            return true;
        }

        if (first.isEmpty() | second.isEmpty()) {
            return false;
        }

        // we optimise getOpposite, so we can use it
        // secondly, use our cache to retrieve sliced shape
        final VoxelShape newFirst = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$getFaceShapeClamped(direction);
        if (newFirst.isEmpty()) {
            return false;
        }
        final VoxelShape newSecond = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$getFaceShapeClamped(direction.getOpposite());
        if (newSecond.isEmpty()) {
            return false;
        }

        return !joinIsNotEmpty(newFirst, newSecond, BooleanOp.ONLY_FIRST);
        // Paper end - optimise collisions
    }

    public static VoxelShape getFaceShape(VoxelShape shape, Direction direction) {
        return ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape).moonrise$getFaceShapeClamped(direction); // Paper - optimise collisions
    }

    // Paper start - optimise collisions
    private static boolean mergedMayOccludeBlock(final VoxelShape shape1, final VoxelShape shape2) {
        // if the combined bounds of the two shapes cannot occlude, then neither can the merged
        final AABB bounds1 = shape1.bounds();
        final AABB bounds2 = shape2.bounds();

        final double minX = Math.min(bounds1.minX, bounds2.minX);
        final double minY = Math.min(bounds1.minY, bounds2.minY);
        final double minZ = Math.min(bounds1.minZ, bounds2.minZ);

        final double maxX = Math.max(bounds1.maxX, bounds2.maxX);
        final double maxY = Math.max(bounds1.maxY, bounds2.maxY);
        final double maxZ = Math.max(bounds1.maxZ, bounds2.maxZ);

        return (minX <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && maxX >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) &&
            (minY <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && maxY >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) &&
            (minZ <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && maxZ >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON));
    }
    // Paper end - optimise collisions

    // Paper start - optimise collisions
    public static boolean mergedFaceOccludes(final VoxelShape first, final VoxelShape second, final Direction direction) {
        // see if any of the shapes on their own occludes, only if cached
        if (((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$occludesFullBlockIfCached() || ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$occludesFullBlockIfCached()) {
            return true;
        }

        if (first.isEmpty() & second.isEmpty()) {
            return false;
        }

        // we optimise getOpposite, so we can use it
        // secondly, use our cache to retrieve sliced shape
        final VoxelShape newFirst = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)first).moonrise$getFaceShapeClamped(direction);
        final VoxelShape newSecond = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)second).moonrise$getFaceShapeClamped(direction.getOpposite());

        // see if any of the shapes on their own occludes, only if cached
        if (((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)newFirst).moonrise$occludesFullBlockIfCached() || ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)newSecond).moonrise$occludesFullBlockIfCached()) {
            return true;
        }

        final boolean firstEmpty = newFirst.isEmpty();
        final boolean secondEmpty = newSecond.isEmpty();

        if (firstEmpty & secondEmpty) {
            return false;
        }

        if (firstEmpty | secondEmpty) {
            return secondEmpty ? ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)newFirst).moonrise$occludesFullBlock() : ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)newSecond).moonrise$occludesFullBlock();
        }

        if (newFirst == newSecond) {
            return ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)newFirst).moonrise$occludesFullBlock();
        }

        return mergedMayOccludeBlock(newFirst, newSecond) && ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)newFirst).moonrise$orUnoptimized(newSecond)).moonrise$occludesFullBlock();
    }
    // Paper end - optimise collisions

    // Paper start - optimise collisions
    public static boolean faceShapeOccludes(final VoxelShape shape1, final VoxelShape shape2) {
        if (((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape1).moonrise$occludesFullBlockIfCached() || ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape2).moonrise$occludesFullBlockIfCached()) {
            return true;
        }

        final boolean s1Empty = shape1.isEmpty();
        final boolean s2Empty = shape2.isEmpty();
        if (s1Empty & s2Empty) {
            return false;
        }

        if (s1Empty | s2Empty) {
            return s2Empty ? ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape1).moonrise$occludesFullBlock() : ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape2).moonrise$occludesFullBlock();
        }

        if (shape1 == shape2) {
            return ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape1).moonrise$occludesFullBlock();
        }

        return mergedMayOccludeBlock(shape1, shape2) && ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)shape1).moonrise$orUnoptimized(shape2)).moonrise$occludesFullBlock();
        // Paper end - optimise collisions
    }

    @VisibleForTesting
    protected static IndexMerger createIndexMerger(int size, DoubleList first, DoubleList second, boolean includeFirst, boolean includeSecond) {
        int i = first.size() - 1;
        int j = second.size() - 1;
        if (first instanceof CubePointRange && second instanceof CubePointRange) {
            long l = lcm(i, j);
            if ((long)size * l <= 256L) {
                return new DiscreteCubeMerger(i, j);
            }
        }

        if (first.getDouble(i) < second.getDouble(0) - 1.0E-7) {
            return new NonOverlappingMerger(first, second, false);
        } else if (second.getDouble(j) < first.getDouble(0) - 1.0E-7) {
            return new NonOverlappingMerger(second, first, true);
        } else {
            return (IndexMerger)(i == j && Objects.equals(first, second)
                ? new IdenticalMerger(first)
                : new IndirectMerger(first, second, includeFirst, includeSecond));
        }
    }

    public interface DoubleLineConsumer {
        void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }
}
