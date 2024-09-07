package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;

public abstract class OneShot<E extends LivingEntity> implements BehaviorControl<E>, Trigger<E> {
    private Behavior.Status status = Behavior.Status.STOPPED;

    @Override
    public final Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel world, E entity, long time) {
        if (this.trigger(world, entity, time)) {
            this.status = Behavior.Status.RUNNING;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public final void tickOrStop(ServerLevel world, E entity, long time) {
        this.doStop(world, entity, time);
    }

    @Override
    public final void doStop(ServerLevel world, E entity, long time) {
        this.status = Behavior.Status.STOPPED;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }
}
