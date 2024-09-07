package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.frog.Frog;

public class Croak extends Behavior<Frog> {
    private static final int CROAK_TICKS = 60;
    private static final int TIME_OUT_DURATION = 100;
    private int croakCounter;

    public Croak() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), 100);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Frog entity) {
        return entity.getPose() == Pose.STANDING;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Frog entity, long time) {
        return this.croakCounter < 60;
    }

    @Override
    protected void start(ServerLevel serverLevel, Frog frog, long l) {
        if (!frog.isInLiquid()) {
            frog.setPose(Pose.CROAKING);
            this.croakCounter = 0;
        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Frog frog, long l) {
        frog.setPose(Pose.STANDING);
    }

    @Override
    protected void tick(ServerLevel serverLevel, Frog frog, long l) {
        this.croakCounter++;
    }
}
