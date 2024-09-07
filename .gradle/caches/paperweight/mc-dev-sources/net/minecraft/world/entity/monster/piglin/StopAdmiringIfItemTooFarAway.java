package net.minecraft.world.entity.monster.piglin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;

public class StopAdmiringIfItemTooFarAway<E extends Piglin> {
    public static BehaviorControl<LivingEntity> create(int range) {
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.ADMIRING_ITEM), context.registered(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM))
                    .apply(context, (admiringItem, nearestVisibleWantedItem) -> (world, entity, time) -> {
                            if (!entity.getOffhandItem().isEmpty()) {
                                return false;
                            } else {
                                Optional<ItemEntity> optional = context.tryGet(nearestVisibleWantedItem);
                                if (optional.isPresent() && optional.get().closerThan(entity, (double)range)) {
                                    return false;
                                } else {
                                    admiringItem.erase();
                                    return true;
                                }
                            }
                        })
        );
    }
}
