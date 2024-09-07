package net.minecraft.world.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class DoNothing implements BehaviorControl<LivingEntity> {
    private final int minDuration;
    private final int maxDuration;
    private Behavior.Status status = Behavior.Status.STOPPED;
    private long endTimestamp;

    public DoNothing(int minRunTime, int maxRunTime) {
        this.minDuration = minRunTime;
        this.maxDuration = maxRunTime;
    }

    @Override
    public Behavior.Status getStatus() {
        return this.status;
    }

    @Override
    public final boolean tryStart(ServerLevel world, LivingEntity entity, long time) {
        this.status = Behavior.Status.RUNNING;
        int i = this.minDuration + world.getRandom().nextInt(this.maxDuration + 1 - this.minDuration);
        this.endTimestamp = time + (long)i;
        return true;
    }

    @Override
    public final void tickOrStop(ServerLevel world, LivingEntity entity, long time) {
        if (time > this.endTimestamp) {
            this.doStop(world, entity, time);
        }
    }

    @Override
    public final void doStop(ServerLevel world, LivingEntity entity, long time) {
        this.status = Behavior.Status.STOPPED;
    }

    @Override
    public String debugString() {
        return this.getClass().getSimpleName();
    }
}
