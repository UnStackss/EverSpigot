package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;

public class RandomLookAround extends Behavior<Mob> {
    private final IntProvider interval;
    private final float maxYaw;
    private final float minPitch;
    private final float pitchRange;

    public RandomLookAround(IntProvider cooldown, float maxYaw, float minPitch, float maxPitch) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.GAZE_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT));
        if (minPitch > maxPitch) {
            throw new IllegalArgumentException("Minimum pitch is larger than maximum pitch! " + minPitch + " > " + maxPitch);
        } else {
            this.interval = cooldown;
            this.maxYaw = maxYaw;
            this.minPitch = minPitch;
            this.pitchRange = maxPitch - minPitch;
        }
    }

    @Override
    protected void start(ServerLevel serverLevel, Mob mob, long l) {
        RandomSource randomSource = mob.getRandom();
        float f = Mth.clamp(randomSource.nextFloat() * this.pitchRange + this.minPitch, -90.0F, 90.0F);
        float g = Mth.wrapDegrees(mob.getYRot() + 2.0F * randomSource.nextFloat() * this.maxYaw - this.maxYaw);
        Vec3 vec3 = Vec3.directionFromRotation(f, g);
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(mob.getEyePosition().add(vec3)));
        mob.getBrain().setMemory(MemoryModuleType.GAZE_COOLDOWN_TICKS, this.interval.sample(randomSource));
    }
}
