package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;

public class LevelEntityGetterAdapter<T extends EntityAccess> implements LevelEntityGetter<T> {
    private final EntityLookup<T> visibleEntities;
    private final EntitySectionStorage<T> sectionStorage;

    public LevelEntityGetterAdapter(EntityLookup<T> index, EntitySectionStorage<T> cache) {
        this.visibleEntities = index;
        this.sectionStorage = cache;
    }

    @Nullable
    @Override
    public T get(int id) {
        return this.visibleEntities.getEntity(id);
    }

    @Nullable
    @Override
    public T get(UUID uuid) {
        return this.visibleEntities.getEntity(uuid);
    }

    @Override
    public Iterable<T> getAll() {
        return this.visibleEntities.getAllEntities();
    }

    @Override
    public <U extends T> void get(EntityTypeTest<T, U> filter, AbortableIterationConsumer<U> consumer) {
        this.visibleEntities.getEntities(filter, consumer);
    }

    @Override
    public void get(AABB box, Consumer<T> action) {
        this.sectionStorage.getEntities(box, AbortableIterationConsumer.forConsumer(action));
    }

    @Override
    public <U extends T> void get(EntityTypeTest<T, U> filter, AABB box, AbortableIterationConsumer<U> consumer) {
        this.sectionStorage.getEntities(filter, box, consumer);
    }
}
