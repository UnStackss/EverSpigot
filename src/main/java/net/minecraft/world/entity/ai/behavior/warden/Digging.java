package net.minecraft.world.entity.ai.behavior.warden;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;

// CraftBukkit start - imports
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class Digging<E extends Warden> extends Behavior<E> {

    public Digging(int duration) {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), duration);
    }

    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return entity.getRemovalReason() == null;
    }

    protected boolean checkExtraStartConditions(ServerLevel world, E entity) {
        return entity.onGround() || entity.isInWater() || entity.isInLava();
    }

    protected void start(ServerLevel worldserver, E e0, long i) {
        if (e0.onGround()) {
            e0.setPose(Pose.DIGGING);
            e0.playSound(SoundEvents.WARDEN_DIG, 5.0F, 1.0F);
        } else {
            e0.playSound(SoundEvents.WARDEN_AGITATED, 5.0F, 1.0F);
            this.stop(worldserver, e0, i);
        }

    }

    protected void stop(ServerLevel worldserver, E e0, long i) {
        if (e0.getRemovalReason() == null) {
            e0.remove(Entity.RemovalReason.DISCARDED, EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - Add bukkit remove cause
        }

    }
}
