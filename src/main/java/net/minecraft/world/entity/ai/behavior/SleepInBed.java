package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SleepInBed extends Behavior<LivingEntity> {
    public static final int COOLDOWN_AFTER_BEING_WOKEN = 100;
    private long nextOkStartTime;

    public SleepInBed() {
        super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT, MemoryModuleType.LAST_WOKEN, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, LivingEntity entity) {
        if (entity.isPassenger()) {
            return false;
        } else {
            Brain<?> brain = entity.getBrain();
            GlobalPos globalPos = brain.getMemory(MemoryModuleType.HOME).get();
            if (world.dimension() != globalPos.dimension()) {
                return false;
            } else {
                Optional<Long> optional = brain.getMemory(MemoryModuleType.LAST_WOKEN);
                if (optional.isPresent()) {
                    long l = world.getGameTime() - optional.get();
                    if (l > 0L && l < 100L) {
                        return false;
                    }
                }

                BlockState blockState = world.getBlockStateIfLoaded(globalPos.pos()); // Paper - Prevent sync chunk loads when villagers try to find beds
                if (blockState == null) { return false; } // Paper - Prevent sync chunk loads when villagers try to find beds
                return globalPos.pos().closerToCenterThan(entity.position(), 2.0) && blockState.is(BlockTags.BEDS) && !blockState.getValue(BedBlock.OCCUPIED);
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, LivingEntity entity, long time) {
        Optional<GlobalPos> optional = entity.getBrain().getMemory(MemoryModuleType.HOME);
        if (optional.isEmpty()) {
            return false;
        } else {
            BlockPos blockPos = optional.get().pos();
            return entity.getBrain().isActive(Activity.REST)
                && entity.getY() > (double)blockPos.getY() + 0.4
                && blockPos.closerToCenterThan(entity.position(), 1.14);
        }
    }

    @Override
    protected void start(ServerLevel world, LivingEntity entity, long time) {
        if (time > this.nextOkStartTime) {
            Brain<?> brain = entity.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.DOORS_TO_CLOSE)) {
                Set<GlobalPos> set = brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE).get();
                Optional<List<LivingEntity>> optional;
                if (brain.hasMemoryValue(MemoryModuleType.NEAREST_LIVING_ENTITIES)) {
                    optional = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
                } else {
                    optional = Optional.empty();
                }

                InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(world, entity, null, null, set, optional);
            }

            entity.startSleeping(entity.getBrain().getMemory(MemoryModuleType.HOME).get().pos());
        }
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    @Override
    protected void stop(ServerLevel world, LivingEntity entity, long time) {
        if (entity.isSleeping()) {
            entity.stopSleeping();
            this.nextOkStartTime = time + 40L;
        }
    }
}
