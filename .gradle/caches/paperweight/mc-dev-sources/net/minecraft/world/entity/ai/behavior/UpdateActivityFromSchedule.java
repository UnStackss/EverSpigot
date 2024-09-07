package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;

public class UpdateActivityFromSchedule {
    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(context -> context.point((world, entity, time) -> {
                entity.getBrain().updateActivityFromSchedule(world.getDayTime(), world.getGameTime());
                return true;
            }));
    }
}
