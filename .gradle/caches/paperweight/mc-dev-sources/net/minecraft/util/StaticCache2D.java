package net.minecraft.util;

import java.util.Locale;
import java.util.function.Consumer;

public class StaticCache2D<T> {
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final Object[] cache;

    public static <T> StaticCache2D<T> create(int centerX, int centerZ, int radius, StaticCache2D.Initializer<T> getter) {
        int i = centerX - radius;
        int j = centerZ - radius;
        int k = 2 * radius + 1;
        return new StaticCache2D<>(i, j, k, k, getter);
    }

    private StaticCache2D(int minX, int minZ, int maxX, int maxZ, StaticCache2D.Initializer<T> getter) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = maxX;
        this.sizeZ = maxZ;
        this.cache = new Object[this.sizeX * this.sizeZ];

        for (int i = minX; i < minX + maxX; i++) {
            for (int j = minZ; j < minZ + maxZ; j++) {
                this.cache[this.getIndex(i, j)] = getter.get(i, j);
            }
        }
    }

    public void forEach(Consumer<T> callback) {
        for (Object object : this.cache) {
            callback.accept((T)object);
        }
    }

    public T get(int x, int z) {
        if (!this.contains(x, z)) {
            throw new IllegalArgumentException("Requested out of range value (" + x + "," + z + ") from " + this);
        } else {
            return (T)this.cache[this.getIndex(x, z)];
        }
    }

    public boolean contains(int x, int z) {
        int i = x - this.minX;
        int j = z - this.minZ;
        return i >= 0 && i < this.sizeX && j >= 0 && j < this.sizeZ;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "StaticCache2D[%d, %d, %d, %d]", this.minX, this.minZ, this.minX + this.sizeX, this.minZ + this.sizeZ);
    }

    private int getIndex(int x, int z) {
        int i = x - this.minX;
        int j = z - this.minZ;
        return i * this.sizeZ + j;
    }

    @FunctionalInterface
    public interface Initializer<T> {
        T get(int x, int z);
    }
}
