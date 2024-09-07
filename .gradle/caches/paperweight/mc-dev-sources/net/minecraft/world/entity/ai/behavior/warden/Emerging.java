package net.minecraft.world.entity.ai.behavior.warden;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;

public class Emerging<E extends Warden> extends Behavior<E> {
    public Emerging(int duration) {
        super(
            ImmutableMap.of(
                MemoryModuleType.IS_EMERGING,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED
            ),
            duration
        );
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return true;
    }

    @Override
    protected void start(ServerLevel serverLevel, E warden, long l) {
        warden.setPose(Pose.EMERGING);
        warden.playSound(SoundEvents.WARDEN_EMERGE, 5.0F, 1.0F);
    }

    @Override
    protected void stop(ServerLevel serverLevel, E warden, long l) {
        if (warden.hasPose(Pose.EMERGING)) {
            warden.setPose(Pose.STANDING);
        }
    }
}
