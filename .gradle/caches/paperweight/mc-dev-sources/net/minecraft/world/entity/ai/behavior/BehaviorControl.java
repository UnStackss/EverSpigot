package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public interface BehaviorControl<E extends LivingEntity> {
    Behavior.Status getStatus();

    boolean tryStart(ServerLevel world, E entity, long time);

    void tickOrStop(ServerLevel world, E entity, long time);

    void doStop(ServerLevel world, E entity, long time);

    String debugString();
}
