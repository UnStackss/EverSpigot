package net.minecraft.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;

public class ClassTreeIdRegistry {
    public static final int NO_ID_VALUE = -1;
    private final Object2IntMap<Class<?>> classToLastIdCache = Util.make(
        new Object2IntOpenHashMap<>(), object2IntOpenHashMap -> object2IntOpenHashMap.defaultReturnValue(-1)
    );

    public int getLastIdFor(Class<?> clazz) {
        int i = this.classToLastIdCache.getInt(clazz);
        if (i != -1) {
            return i;
        } else {
            Class<?> class_ = clazz;

            while ((class_ = class_.getSuperclass()) != Object.class) {
                int j = this.classToLastIdCache.getInt(class_);
                if (j != -1) {
                    return j;
                }
            }

            return -1;
        }
    }

    public int getCount(Class<?> clazz) {
        return this.getLastIdFor(clazz) + 1;
    }

    public int define(Class<?> clazz) {
        int i = this.getLastIdFor(clazz);
        int j = i == -1 ? 0 : i + 1;
        this.classToLastIdCache.put(clazz, j);
        return j;
    }
}
