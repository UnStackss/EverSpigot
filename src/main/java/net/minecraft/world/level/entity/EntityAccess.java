package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public interface EntityAccess {

    int getId();

    UUID getUUID();

    BlockPos blockPosition();

    AABB getBoundingBox();

    void setLevelCallback(EntityInLevelCallback changeListener);

    Stream<? extends EntityAccess> getSelfAndPassengers();

    Stream<? extends EntityAccess> getPassengersAndSelf();

    void setRemoved(Entity.RemovalReason reason);

    // CraftBukkit start - add Bukkit remove cause
    default void setRemoved(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        this.setRemoved(entity_removalreason);
    }
    // CraftBukkit end

    boolean shouldBeSaved();

    boolean isAlwaysTicking();
}
