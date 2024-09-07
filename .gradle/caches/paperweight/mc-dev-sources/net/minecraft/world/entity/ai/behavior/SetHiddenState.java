package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.apache.commons.lang3.mutable.MutableInt;

public class SetHiddenState {
    private static final int HIDE_TIMEOUT = 300;

    public static BehaviorControl<LivingEntity> create(int maxHiddenSeconds, int distance) {
        int i = maxHiddenSeconds * 20;
        MutableInt mutableInt = new MutableInt(0);
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.HIDING_PLACE), context.present(MemoryModuleType.HEARD_BELL_TIME))
                    .apply(context, (hidingPlace, heardBellTime) -> (world, entity, time) -> {
                            long l = context.<Long>get(heardBellTime);
                            boolean bl = l + 300L <= time;
                            if (mutableInt.getValue() <= i && !bl) {
                                BlockPos blockPos = context.get(hidingPlace).pos();
                                if (blockPos.closerThan(entity.blockPosition(), (double)distance)) {
                                    mutableInt.increment();
                                }

                                return true;
                            } else {
                                heardBellTime.erase();
                                hidingPlace.erase();
                                entity.getBrain().updateActivityFromSchedule(world.getDayTime(), world.getGameTime());
                                mutableInt.setValue(0);
                                return true;
                            }
                        })
        );
    }
}
