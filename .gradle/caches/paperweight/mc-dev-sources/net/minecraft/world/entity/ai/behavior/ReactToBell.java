package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class ReactToBell {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.HEARD_BELL_TIME)).apply(context, heardBellTime -> (world, entity, time) -> {
                        Raid raid = world.getRaidAt(entity.blockPosition());
                        if (raid == null) {
                            entity.getBrain().setActiveActivityIfPossible(Activity.HIDE);
                        }

                        return true;
                    })
        );
    }
}
