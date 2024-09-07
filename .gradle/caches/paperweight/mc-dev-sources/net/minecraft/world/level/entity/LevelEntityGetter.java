package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;

public interface LevelEntityGetter<T extends EntityAccess> {
    @Nullable
    T get(int id);

    @Nullable
    T get(UUID uuid);

    Iterable<T> getAll();

    <U extends T> void get(EntityTypeTest<T, U> filter, AbortableIterationConsumer<U> consumer);

    void get(AABB box, Consumer<T> action);

    <U extends T> void get(EntityTypeTest<T, U> filter, AABB box, AbortableIterationConsumer<U> consumer);
}
