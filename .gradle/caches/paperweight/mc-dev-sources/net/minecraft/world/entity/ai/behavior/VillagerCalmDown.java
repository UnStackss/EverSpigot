package net.minecraft.world.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class VillagerCalmDown {
    private static final int SAFE_DISTANCE_FROM_DANGER = 36;

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.registered(MemoryModuleType.HURT_BY),
                        context.registered(MemoryModuleType.HURT_BY_ENTITY),
                        context.registered(MemoryModuleType.NEAREST_HOSTILE)
                    )
                    .apply(
                        context,
                        (hurtBy, hurtByEntity, nearestHostile) -> (world, entity, time) -> {
                                boolean bl = context.tryGet(hurtBy).isPresent()
                                    || context.tryGet(nearestHostile).isPresent()
                                    || context.<LivingEntity>tryGet(hurtByEntity).filter(hurtByx -> hurtByx.distanceToSqr(entity) <= 36.0).isPresent();
                                if (!bl) {
                                    hurtBy.erase();
                                    hurtByEntity.erase();
                                    entity.getBrain().updateActivityFromSchedule(world.getDayTime(), world.getGameTime());
                                }

                                return true;
                            }
                    )
        );
    }
}
