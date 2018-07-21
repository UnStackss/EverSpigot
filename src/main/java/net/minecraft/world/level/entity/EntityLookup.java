package net.minecraft.world.level.entity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.AbortableIterationConsumer;
import org.slf4j.Logger;

public class EntityLookup<T extends EntityAccess> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Int2ObjectMap<T> byId = new Int2ObjectLinkedOpenHashMap<>();
    private final Map<UUID, T> byUuid = Maps.newHashMap();

    public <U extends T> void getEntities(EntityTypeTest<T, U> filter, AbortableIterationConsumer<U> consumer) {
        for (T entityAccess : this.byId.values()) {
            U entityAccess2 = (U)filter.tryCast(entityAccess);
            if (entityAccess2 != null && consumer.accept(entityAccess2).shouldAbort()) {
                return;
            }
        }
    }

    public Iterable<T> getAllEntities() {
        return Iterables.unmodifiableIterable(this.byId.values());
    }

    public void add(T entity) {
        UUID uUID = entity.getUUID();
        if (this.byUuid.containsKey(uUID)) {
            LOGGER.warn("Duplicate entity UUID {}: {}", uUID, entity);
            // Paper start - extra debug info
            if (entity instanceof net.minecraft.world.entity.Entity) {
                final T old = this.byUuid.get(entity.getUUID());
                if (old instanceof net.minecraft.world.entity.Entity oldCast && oldCast.getId() != entity.getId() && oldCast.valid) {
                    LOGGER.error("Overwrote an existing entity {} with {}", oldCast, entity);
                }
            }
            // Paper end - extra debug info
        } else {
            this.byUuid.put(uUID, entity);
            this.byId.put(entity.getId(), entity);
        }
    }

    public void remove(T entity) {
        this.byUuid.remove(entity.getUUID());
        this.byId.remove(entity.getId());
    }

    @Nullable
    public T getEntity(int id) {
        return this.byId.get(id);
    }

    @Nullable
    public T getEntity(UUID uuid) {
        return this.byUuid.get(uuid);
    }

    public int count() {
        return this.byUuid.size();
    }
}
