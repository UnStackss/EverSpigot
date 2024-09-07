package net.minecraft.world.entity.ai.behavior;

import java.util.function.BiPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class DismountOrSkipMounting {
    public static <E extends LivingEntity> BehaviorControl<E> create(int range, BiPredicate<E, Entity> alternativeRideCondition) {
        return BehaviorBuilder.create(
            context -> context.group(context.registered(MemoryModuleType.RIDE_TARGET)).apply(context, rideTarget -> (world, entity, time) -> {
                        Entity entity2 = entity.getVehicle();
                        Entity entity3 = context.<Entity>tryGet(rideTarget).orElse(null);
                        if (entity2 == null && entity3 == null) {
                            return false;
                        } else {
                            Entity entity4 = entity2 == null ? entity3 : entity2;
                            if (isVehicleValid(entity, entity4, range) && !alternativeRideCondition.test(entity, entity4)) {
                                return false;
                            } else {
                                entity.stopRiding();
                                rideTarget.erase();
                                return true;
                            }
                        }
                    })
        );
    }

    private static boolean isVehicleValid(LivingEntity entity, Entity vehicle, int range) {
        return vehicle.isAlive() && vehicle.closerThan(entity, (double)range) && vehicle.level() == entity.level();
    }
}
