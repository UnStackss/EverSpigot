package net.minecraft.world.phys.shapes;

import java.util.BitSet;
import net.minecraft.core.Direction;

public final class BitSetDiscreteVoxelShape extends DiscreteVoxelShape {
    private final BitSet storage;
    private int xMin;
    private int yMin;
    private int zMin;
    private int xMax;
    private int yMax;
    private int zMax;

    public BitSetDiscreteVoxelShape(int sizeX, int sizeY, int sizeZ) {
        super(sizeX, sizeY, sizeZ);
        this.storage = new BitSet(sizeX * sizeY * sizeZ);
        this.xMin = sizeX;
        this.yMin = sizeY;
        this.zMin = sizeZ;
    }

    public static BitSetDiscreteVoxelShape withFilledBounds(int sizeX, int sizeY, int sizeZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(sizeX, sizeY, sizeZ);
        bitSetDiscreteVoxelShape.xMin = minX;
        bitSetDiscreteVoxelShape.yMin = minY;
        bitSetDiscreteVoxelShape.zMin = minZ;
        bitSetDiscreteVoxelShape.xMax = maxX;
        bitSetDiscreteVoxelShape.yMax = maxY;
        bitSetDiscreteVoxelShape.zMax = maxZ;

        for (int i = minX; i < maxX; i++) {
            for (int j = minY; j < maxY; j++) {
                for (int k = minZ; k < maxZ; k++) {
                    bitSetDiscreteVoxelShape.fillUpdateBounds(i, j, k, false);
                }
            }
        }

        return bitSetDiscreteVoxelShape;
    }

    public BitSetDiscreteVoxelShape(DiscreteVoxelShape other) {
        super(other.xSize, other.ySize, other.zSize);
        if (other instanceof BitSetDiscreteVoxelShape) {
            this.storage = (BitSet)((BitSetDiscreteVoxelShape)other).storage.clone();
        } else {
            this.storage = new BitSet(this.xSize * this.ySize * this.zSize);

            for (int i = 0; i < this.xSize; i++) {
                for (int j = 0; j < this.ySize; j++) {
                    for (int k = 0; k < this.zSize; k++) {
                        if (other.isFull(i, j, k)) {
                            this.storage.set(this.getIndex(i, j, k));
                        }
                    }
                }
            }
        }

        this.xMin = other.firstFull(Direction.Axis.X);
        this.yMin = other.firstFull(Direction.Axis.Y);
        this.zMin = other.firstFull(Direction.Axis.Z);
        this.xMax = other.lastFull(Direction.Axis.X);
        this.yMax = other.lastFull(Direction.Axis.Y);
        this.zMax = other.lastFull(Direction.Axis.Z);
    }

    protected int getIndex(int x, int y, int z) {
        return (x * this.ySize + y) * this.zSize + z;
    }

    @Override
    public boolean isFull(int x, int y, int z) {
        return this.storage.get(this.getIndex(x, y, z));
    }

    private void fillUpdateBounds(int x, int y, int z, boolean updateBounds) {
        this.storage.set(this.getIndex(x, y, z));
        if (updateBounds) {
            this.xMin = Math.min(this.xMin, x);
            this.yMin = Math.min(this.yMin, y);
            this.zMin = Math.min(this.zMin, z);
            this.xMax = Math.max(this.xMax, x + 1);
            this.yMax = Math.max(this.yMax, y + 1);
            this.zMax = Math.max(this.zMax, z + 1);
        }
    }

    @Override
    public void fill(int x, int y, int z) {
        this.fillUpdateBounds(x, y, z, true);
    }

    @Override
    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    @Override
    public int firstFull(Direction.Axis axis) {
        return axis.choose(this.xMin, this.yMin, this.zMin);
    }

    @Override
    public int lastFull(Direction.Axis axis) {
        return axis.choose(this.xMax, this.yMax, this.zMax);
    }

    static BitSetDiscreteVoxelShape join(
        DiscreteVoxelShape first, DiscreteVoxelShape second, IndexMerger xPoints, IndexMerger yPoints, IndexMerger zPoints, BooleanOp function
    ) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(xPoints.size() - 1, yPoints.size() - 1, zPoints.size() - 1);
        int[] is = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        xPoints.forMergedIndexes((x1, x2, xIndex) -> {
            boolean[] bls = new boolean[]{false};
            yPoints.forMergedIndexes((y1, y2, yIndex) -> {
                boolean[] bls2 = new boolean[]{false};
                zPoints.forMergedIndexes((z1, z2, zIndex) -> {
                    if (function.apply(first.isFullWide(x1, y1, z1), second.isFullWide(x2, y2, z2))) {
                        bitSetDiscreteVoxelShape.storage.set(bitSetDiscreteVoxelShape.getIndex(xIndex, yIndex, zIndex));
                        is[2] = Math.min(is[2], zIndex);
                        is[5] = Math.max(is[5], zIndex);
                        bls2[0] = true;
                    }

                    return true;
                });
                if (bls2[0]) {
                    is[1] = Math.min(is[1], yIndex);
                    is[4] = Math.max(is[4], yIndex);
                    bls[0] = true;
                }

                return true;
            });
            if (bls[0]) {
                is[0] = Math.min(is[0], xIndex);
                is[3] = Math.max(is[3], xIndex);
            }

            return true;
        });
        bitSetDiscreteVoxelShape.xMin = is[0];
        bitSetDiscreteVoxelShape.yMin = is[1];
        bitSetDiscreteVoxelShape.zMin = is[2];
        bitSetDiscreteVoxelShape.xMax = is[3] + 1;
        bitSetDiscreteVoxelShape.yMax = is[4] + 1;
        bitSetDiscreteVoxelShape.zMax = is[5] + 1;
        return bitSetDiscreteVoxelShape;
    }

    protected static void forAllBoxes(DiscreteVoxelShape voxelSet, DiscreteVoxelShape.IntLineConsumer callback, boolean coalesce) {
        BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = new BitSetDiscreteVoxelShape(voxelSet);

        for (int i = 0; i < bitSetDiscreteVoxelShape.ySize; i++) {
            for (int j = 0; j < bitSetDiscreteVoxelShape.xSize; j++) {
                int k = -1;

                for (int l = 0; l <= bitSetDiscreteVoxelShape.zSize; l++) {
                    if (bitSetDiscreteVoxelShape.isFullWide(j, i, l)) {
                        if (coalesce) {
                            if (k == -1) {
                                k = l;
                            }
                        } else {
                            callback.consume(j, i, l, j + 1, i + 1, l + 1);
                        }
                    } else if (k != -1) {
                        int m = j;
                        int n = i;
                        bitSetDiscreteVoxelShape.clearZStrip(k, l, j, i);

                        while (bitSetDiscreteVoxelShape.isZStripFull(k, l, m + 1, i)) {
                            bitSetDiscreteVoxelShape.clearZStrip(k, l, m + 1, i);
                            m++;
                        }

                        while (bitSetDiscreteVoxelShape.isXZRectangleFull(j, m + 1, k, l, n + 1)) {
                            for (int o = j; o <= m; o++) {
                                bitSetDiscreteVoxelShape.clearZStrip(k, l, o, n + 1);
                            }

                            n++;
                        }

                        callback.consume(j, i, k, m + 1, n + 1, l);
                        k = -1;
                    }
                }
            }
        }
    }

    private boolean isZStripFull(int z1, int z2, int x, int y) {
        return x < this.xSize && y < this.ySize && this.storage.nextClearBit(this.getIndex(x, y, z1)) >= this.getIndex(x, y, z2);
    }

    private boolean isXZRectangleFull(int x1, int x2, int z1, int z2, int y) {
        for (int i = x1; i < x2; i++) {
            if (!this.isZStripFull(z1, z2, i, y)) {
                return false;
            }
        }

        return true;
    }

    private void clearZStrip(int z1, int z2, int x, int y) {
        this.storage.clear(this.getIndex(x, y, z1), this.getIndex(x, y, z2));
    }

    public boolean isInterior(int x, int y, int z) {
        boolean bl = x > 0 && x < this.xSize - 1 && y > 0 && y < this.ySize - 1 && z > 0 && z < this.zSize - 1;
        return bl
            && this.isFull(x, y, z)
            && this.isFull(x - 1, y, z)
            && this.isFull(x + 1, y, z)
            && this.isFull(x, y - 1, z)
            && this.isFull(x, y + 1, z)
            && this.isFull(x, y, z - 1)
            && this.isFull(x, y, z + 1);
    }
}
